package com.github.naofum.daisydownloader.apps;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.github.naofum.daisydownloader.adapter.DaisyBookAdapter;
import com.github.naofum.daisydownloader.base.DaisyEbookReaderBaseActivity;
import com.github.naofum.daisydownloader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.DaisyBookInfo;
import com.github.naofum.daisydownloader.player.IntentController;
import com.github.naofum.daisydownloader.sqlite.SQLiteDaisyBookHelper;
import com.github.naofum.daisydownloader.utils.Constants;
import com.github.naofum.daisydownloader.utils.DaisyBookUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;

import com.actionbarsherlock.view.MenuItem;
import com.github.naofum.daisydownloader.R;

import ly.count.android.api.Countly;

/**
 * The Class DaisyReaderDownloadBooks.
 */
@SuppressLint("NewApi")
public class DaisyReaderDownloadBooks extends DaisyEbookReaderBaseActivity {

    private String mLink;
    private SQLiteDaisyBookHelper mSql;
    private DaisyBookAdapter mDaisyBookAdapter;
    private String mName;
    private AsyncTask mTask;
    private List<DaisyBookInfo> mlistDaisyBook;
    private List<DaisyBookInfo> mListDaisyBookOriginal;
    private DaisyBookInfo mDaisyBook;
    private EditText mTextSearch;
    public static final String PATH = Environment.getExternalStorageDirectory().toString()
            + Constants.FOLDER_DOWNLOADED + "/";
    private ProgressDialog mProgressDialog;
    private AlertDialog alertDialog;

    private static final int MAX_PROGRESS = 100;
    private static final int SIZE = 8192;
    private static final int BYTE_VALUE = 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_books);

        mTextSearch = (EditText) findViewById(R.id.edit_text_search);
        mLink = getIntent().getStringExtra(Constants.LINK_WEBSITE);
        String websiteName = getIntent().getStringExtra(Constants.NAME_WEBSITE);

        mSql = new SQLiteDaisyBookHelper(DaisyReaderDownloadBooks.this);
        mSql.deleteAllDaisyBook(Constants.TYPE_DOWNLOAD_BOOK);
        createDownloadData();
        mlistDaisyBook = mSql.getAllDaisyBook(Constants.TYPE_DOWNLOAD_BOOK);
        mDaisyBookAdapter = new DaisyBookAdapter(DaisyReaderDownloadBooks.this, mlistDaisyBook);
        ListView listDownload = (ListView) findViewById(R.id.list_view_download_books);
        listDownload.setAdapter(mDaisyBookAdapter);
        listDownload.setOnItemClickListener(onItemClick);
        mListDaisyBookOriginal = new ArrayList<DaisyBookInfo>(mlistDaisyBook);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(websiteName.length() != 0 ? websiteName : "");

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case android.R.id.home:
            backToTopScreen();
            break;

        default:
            return super.onOptionsItemSelected(item);
        }
        return false;
    }

    /**
     * Wirte data to sqlite from metadata
     */
    private void createDownloadData() {
        try {
            AssetManager assetManager = getAssets();
            InputStream databaseInputStream = assetManager.open(Constants.META_DATA_FILE_NAME);
            MetaDataHandler metadata = new MetaDataHandler();
            NodeList nList = metadata.readDataDownloadFromXmlFile(databaseInputStream, mLink);
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;
                    String author = eElement.getElementsByTagName(Constants.ATT_AUTHOR).item(0)
                            .getTextContent();
                    String publisher = eElement.getElementsByTagName(Constants.ATT_PUBLISHER)
                            .item(0).getTextContent();
                    String path = eElement.getAttribute(Constants.ATT_LINK);
                    String title = eElement.getElementsByTagName(Constants.ATT_TITLE).item(0)
                            .getTextContent();
                    String date = eElement.getElementsByTagName(Constants.ATT_DATE).item(0)
                            .getTextContent();
                    DaisyBookInfo daisyBook = new DaisyBookInfo("", title, path, author, publisher,
                            date, 1);
                    mSql.addDaisyBook(daisyBook, Constants.TYPE_DOWNLOAD_BOOK);
                }
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
            ex.writeLogException();
        }
    }

    private OnItemClickListener onItemClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
            final DaisyBookInfo daisyBook = mlistDaisyBook.get(position);
            downloadABook(position);
        }
    };

    /**
     * Run asyn task.
     * 
     * @param params the params
     */
    private void runAsynTask(Object params[]) {
        mTask = new DownloadFileFromURL();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            mTask.execute(params);
        }
    }

    /**
     * Run asyn task.
     *
     * @param params the params
     */
    private void runAsynCreateZipTask(Object params[]) {
        mTask = new CreateZip();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            mTask.execute(params);
        }
    }

    /**
     * Run asyn task.
     *
     * @param params the params
     */
    private void runAsynCreateGovTask(Object params[]) {
        mTask = new CreateGov();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            mTask.execute(params);
        }
    }

    /**
     * Check storage.
     * 
     * @param link the link
     * @return true, if successful
     */
    private int checkStorage(String link) {
        int result = 0;
        try {
            java.net.URL url = new java.net.URL(link);
            URLConnection conection = url.openConnection();
            conection.connect();
            int lenghtOfFile = conection.getContentLength();

            StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            long blockSize = statFs.getBlockSize();
            long freeSize = statFs.getFreeBlocks() * blockSize;

            if (freeSize > lenghtOfFile) {
                result = 1;
            }
        } catch (Exception e) {
            result = 2;
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
            ex.writeLogException();
        }
        return result;
    }

    /**
     * Create folder if not exists
     * 
     * @return
     */
    private boolean checkFolderIsExist() {
        boolean result = false;
        File folder = new File(PATH);
        result = folder.exists();
        if (!result) {
            result = folder.mkdir();
        }
        return result;
    }

    /**
     * handle search book when text changed.
     */
    private void handleSearchBook() {
        mTextSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mListDaisyBookOriginal != null && mListDaisyBookOriginal.size() != 0) {
                    mlistDaisyBook = DaisyBookUtil.searchBookWithText(s, mlistDaisyBook,
                            mListDaisyBookOriginal);
                    mDaisyBookAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * Background Async Task to download file
     * */
    class DownloadFileFromURL extends AsyncTask<String, Integer, Boolean> {
        /**
         * Before starting background thread Show Progress Bar Dialog
         * */

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(DaisyReaderDownloadBooks.this);
            mProgressDialog.setMessage(DaisyReaderDownloadBooks.this
                    .getString(R.string.message_downloading_file));
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(MAX_PROGRESS);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pushToDialogOptions(DaisyReaderDownloadBooks.this
                                    .getString(R.string.message_confirm_exit_download));
                        }
                    });
                }
            });
            mProgressDialog.show();
        }

        /**
         * Downloading file in background thread
         * */
        @Override
        protected Boolean doInBackground(String... params) {
            int count;
            boolean result = false;
            String link = params[0];
            try {
                java.net.URL url = new java.net.URL(link);
                URLConnection conection = url.openConnection();
                conection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:45.0) Gecko/20100101 Firefox/45.0");
                conection.setRequestProperty("Accept", "text/html");
                conection.setRequestProperty("Accept-Language", "ja");
                conection.setRequestProperty("Accept-Encoding", "deflate");
                conection.connect();
                Map headers = conection.getHeaderFields();
                Iterator headerIt = headers.keySet().iterator();
                String header = null;
                while(headerIt.hasNext()){
                    String headerKey = (String)headerIt.next();
                    header += headerKey + "：" + headers.get(headerKey) + "\r\n";
                }
                long startTime = System.currentTimeMillis();
                // this will be useful so that you can show a tipical 0-100%
                // progress bar
                int lenghtOfFile = conection.getContentLength();
                // download the file
                InputStream input = new BufferedInputStream(url.openStream(), SIZE);
                // Output stream
                String splitString[] = link.split("/");
                mName = splitString[splitString.length - 1];
                if (mName.equals("ncc.html")) {
                    mName = mName + ".tmp";
                }
                OutputStream output = new FileOutputStream(PATH + mName);
                byte data[] = new byte[BYTE_VALUE];
                long total = 0;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        File file = new File(PATH + mName);
                        if (file.delete()) {
                            Log.i("Delete", "Deleted temporary file, " + mName);
                        } else {
                            Log.i("Delete", "Cannot delete temporary file, " + mName);
                        }
                        break;
                    } else {
                        total += count;
                        // publishing the progress....
                        // After this onProgressUpdate will be called
                        publishProgress((int) ((total * MAX_PROGRESS) / lenghtOfFile));
                        // writing data to file
                        output.write(data, 0, count);
                    }
                }
                // Record the time taken for the download excluding local cleanup.
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                String timeTaken = Long.toString(elapsedTime);
                
                // flushing output
                output.flush();
                // closing streams
                output.close();
                input.close();
                
                // Record the book download completed successfully 
                HashMap<String, String> results = new HashMap<String, String> ();
                results.put("URL", link);
                results.put("FileSize", Integer.toString(count));
                results.put("DurationIn(ms)", timeTaken);
                Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_COMPLETED, results, 1);
                result = true;
            } catch (Exception e) {
            	HashMap<String, String> results = new HashMap<String, String> ();
            	results.put("URL", link);
            	results.put("Exception", e.getMessage());
            	Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_FAILED, results, 1);
                result = false;
                mTask.cancel(true);
                mProgressDialog.dismiss();
                // show error message if an error occurs while connecting to the
                // resource
                final PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        IntentController intent = new IntentController(
                                DaisyReaderDownloadBooks.this);
                        ex.showDialogDowloadException(intent);
                    }
                });
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);
        }

        /**
         * After completing background task Dismiss the progress dialogs
         * **/
        @Override
        protected void onPostExecute(Boolean result) {
            if (alertDialog != null) {
                alertDialog.dismiss();
            }
            mProgressDialog.dismiss();
            try {
                if (result) {
                    String params[] = { "" };
                    if (mName.equals("ncc.html")) {
                        runAsynCreateZipTask(params);
                    } else {
                        runAsynCreateGovTask(params);
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                ex.writeLogException();
            }
        }
    }

    /**
     * Background Async Task to download file
     * */
    class CreateZip extends AsyncTask<String, Integer, Boolean> {
        /**
         * Before starting background thread Show Progress Bar Dialog
         * */

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(DaisyReaderDownloadBooks.this);
            mProgressDialog.setMessage(DaisyReaderDownloadBooks.this
                    .getString(R.string.message_downloading_file));
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(MAX_PROGRESS);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pushToDialogOptions(DaisyReaderDownloadBooks.this
                                    .getString(R.string.message_confirm_exit_download));
                        }
                    });
                }
            });
            mProgressDialog.show();
        }

        /**
         * Downloading file in background thread
         * */
        @Override
        protected Boolean doInBackground(String... params) {
            int count;
            long totalcount = 0;
            boolean result = false;
            long startTime = System.currentTimeMillis();
            String link = mDaisyBook.getPath();
            String split[] = link.split("/");
            String base = link.substring(0, link.length() - split[split.length - 1].length());
            try {
                //parse ncc.html
                List<String> links = new ArrayList<String>();
                links.add("ncc.html");
                Document document = Jsoup.parse(new FileInputStream(PATH + mName), "UTF-8", PATH);
                Elements elements = document.select("a");
                for (org.jsoup.nodes.Element element : elements) {
                    add(links, removeS(element.attr("href")));
                }
                String l[] = links.toArray(new String[links.size()]);
                //parse smil
                for (int i = 0; i < l.length; i++) {
                    if (l[i].equals("ncc.html")) {
                        //
                    } else {
                        Document doc = Jsoup.connect(base + l[i]).parser(Parser.xmlParser()).get();
                            Elements els = doc.select("text");
                            for (org.jsoup.nodes.Element element : els) {
                            add(links, removeS(element.attr("src").toString()));
                        }
                            els = doc.select("audio");
                        for (org.jsoup.nodes.Element element : els) {
                            add(links, removeS(element.attr("src")));
                        }
                    }
                    publishProgress(0, (int) (10 * i / l.length));
                }
                l = links.toArray(new String[links.size()]);
                //parse html
                for (int i = 0; i < l.length; i++) {
                    if (l[i].equals("ncc.html") || l[i].endsWith(".smil") || l[i].endsWith(".mp3")) {
                        //
                    } else {
                        Document doc = Jsoup.connect(base + l[i]).get();
                        Elements els = doc.select("img");
                        for (org.jsoup.nodes.Element element : els) {
                            add(links, removeS(element.attr("src")));
                        }
                        els = doc.select("link");
                        for (org.jsoup.nodes.Element element : els) {
                            add(links, removeS(element.attr("href")));
                        }
                    }
                    publishProgress(0, (int) (10 * i / l.length) + 10);
                }
                links.add("master.smil");

                String outputFile = PATH + split[split.length - 2] + ".zip";
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile));
                for (int i = 0; i < links.size(); i++) {
                    String filename = links.get(i);
                    ZipEntry ze = new ZipEntry(split[split.length - 2] + "/" + filename);
                    zos.putNextEntry(ze);

                    java.net.URL url = new java.net.URL(base + links.get(i));
                    URLConnection conection = url.openConnection();
//                conection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:45.0) Gecko/20100101 Firefox/45.0");
                    conection.setRequestProperty("Accept", "text/html");
                    conection.setRequestProperty("Accept-Language", "ja");
                    conection.setRequestProperty("Accept-Encoding", "deflate");
                    conection.connect();
                    // this will be useful so that you can show a tipical 0-100%
                    // progress bar
                    int lenghtOfFile = conection.getContentLength();
                    // download the file
                    InputStream input = new BufferedInputStream(url.openStream(), SIZE);
                    byte data[] = new byte[BYTE_VALUE];
                    long total = 0;
                    while ((count = input.read(data)) != -1) {
                        if (isCancelled()) {
                            File file = new File(PATH + mName);
                            if (file.delete()) {
                                Log.i("Delete", "Deleted temporary file, " + mName);
                            } else {
                                Log.i("Delete", "Cannot delete temporary file, " + mName);
                            }
                            break;
                        } else {
                            total += count;
                            // publishing the progress....
                            // After this onProgressUpdate will be called
                            publishProgress((int) ((total * MAX_PROGRESS) / lenghtOfFile), (int) (80 * i / links.size()));
                            // writing data to file
                            zos.write(data, 0, count);
                        }
                    }

                    zos.closeEntry();
                    input.close();
                    totalcount += total;
                }
                zos.close();

                // Record the time taken for the download excluding local cleanup.
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                String timeTaken = Long.toString(elapsedTime);

                // Record the book download completed successfully
                HashMap<String, String> results = new HashMap<String, String> ();
                results.put("URL", link);
                results.put("FileSize", Long.toString(totalcount));
                results.put("DurationIn(ms)", timeTaken);
                Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_COMPLETED, results, 1);
                result = true;
            } catch (Exception e) {
                HashMap<String, String> results = new HashMap<String, String> ();
                results.put("URL", link);
                results.put("Exception", e.getMessage());
                Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_FAILED, results, 1);
                result = false;
                mTask.cancel(true);
                mProgressDialog.dismiss();
                // show error message if an error occurs while connecting to the
                // resource
                final PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        IntentController intent = new IntentController(
                                DaisyReaderDownloadBooks.this);
                        ex.showDialogDowloadException(intent);
                    }
                });
            }
            return result;
        }


        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);
            mProgressDialog.setSecondaryProgress(values[1]);
        }

        /**
         * After completing background task Dismiss the progress dialogs
         * **/
        @Override
        protected void onPostExecute(Boolean result) {
            if (alertDialog != null) {
                alertDialog.dismiss();
            }
            mProgressDialog.dismiss();
            try {
                if (result) {
                    DaisyBook daisyBook = new DaisyBook();
                    String path = PATH + mName;
                    if (DaisyBookUtil.findDaisyFormat(path) == Constants.DAISY_202_FORMAT) {
                        daisyBook = DaisyBookUtil.getDaisy202Book(path);
                    } else {
                        daisyBook = DaisyBookUtil.getDaisy30Book(path);
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                ex.writeLogException();
            }
        }
    }

    /**
     * Background Async Task to download file
     * */
    class CreateGov extends AsyncTask<String, Integer, Boolean> {
        /**
         * Before starting background thread Show Progress Bar Dialog
         * */

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(DaisyReaderDownloadBooks.this);
            mProgressDialog.setMessage(DaisyReaderDownloadBooks.this
                    .getString(R.string.message_downloading_file));
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(MAX_PROGRESS);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pushToDialogOptions(DaisyReaderDownloadBooks.this
                                    .getString(R.string.message_confirm_exit_download));
                        }
                    });
                }
            });
            mProgressDialog.show();
        }

        /**
         * Downloading file in background thread
         * */
        @Override
        protected Boolean doInBackground(String... params) {
            int count;
            long totalcount = 0;
            boolean result = false;
            long startTime = System.currentTimeMillis();
            String link = mDaisyBook.getPath();
            String split[] = link.split("/");
            String base = link.substring(0, link.length() - split[split.length - 1].length());
            try {
                //parse ncc.html
                List<String> links = parseHtml();
//                List<String> links = new ArrayList<String>();
//                Document document = Jsoup.parse(new FileInputStream(PATH + mName), "UTF-8", PATH);
//                Elements elements = document.select("tbody>tr");
//                for (org.jsoup.nodes.Element element : elements) {
//                    if (element.select("td").size() > 1) {
//                        links.add(element.select("td>strong").text());
//                        links.add(element.select("td>a").get(0).attr("href"));
//                    }
//                }

                Map<String, String> metadata = new HashMap<String, String>();
                metadata.put("dc:title", mDaisyBook.getTitle());
                metadata.put("dc:creator", mDaisyBook.getAuthor());
                metadata.put("dc:publisher", mDaisyBook.getPublisher());

                mName = split[split.length - 2] + ".zip";
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(PATH + mName));
                ZipEntry ze = new ZipEntry(split[split.length - 2] + "/ncc.html");
                zos.putNextEntry(ze);
                zos.write(getNcc(links, metadata).getBytes());
                zos.closeEntry();

                for (int i = 0; i < links.size(); i++) {
                    String linka = links.get(i);
                    if (linka.endsWith(".mp3")) {
                        ze = new ZipEntry(split[split.length - 2] + String.format("/file%1$04d.smil", i));
                        zos.putNextEntry(ze);
                        zos.write(getSmil(i, metadata).getBytes());
                        zos.closeEntry();

                        ze = new ZipEntry(split[split.length - 2] + String.format("/file%1$04d.mp3", i));
                        zos.putNextEntry(ze);

                        java.net.URL url = new java.net.URL(linka);
                        URLConnection conection = url.openConnection();
//                conection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:45.0) Gecko/20100101 Firefox/45.0");
                        conection.setRequestProperty("Accept", "text/html");
                        conection.setRequestProperty("Accept-Language", "ja");
                        conection.setRequestProperty("Accept-Encoding", "deflate");
                        conection.connect();
                        // this will be useful so that you can show a tipical 0-100%
                        // progress bar
                        int lenghtOfFile = conection.getContentLength();
                        // download the file
                        InputStream input = new BufferedInputStream(url.openStream(), SIZE);
                        byte data[] = new byte[BYTE_VALUE];
                        long total = 0;
                        while ((count = input.read(data)) != -1) {
                            if (isCancelled()) {
                                File file = new File(PATH + mName);
                                if (file.delete()) {
                                    Log.i("Delete", "Deleted temporary file, " + mName);
                                } else {
                                    Log.i("Delete", "Cannot delete temporary file, " + mName);
                                }
                                break;
                            } else {
                                total += count;
                                // publishing the progress....
                                // After this onProgressUpdate will be called
                                publishProgress((int) ((total * MAX_PROGRESS) / lenghtOfFile), (int) (100 * i / links.size()));
                                // writing data to file
                                zos.write(data, 0, count);
                            }
                        }

                        zos.closeEntry();
                        input.close();
                        totalcount += total;
                    }
                }
                zos.close();

                // Record the time taken for the download excluding local cleanup.
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                String timeTaken = Long.toString(elapsedTime);

                // Record the book download completed successfully
                HashMap<String, String> results = new HashMap<String, String> ();
                results.put("URL", link);
                results.put("FileSize", Long.toString(totalcount));
                results.put("DurationIn(ms)", timeTaken);
                Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_COMPLETED, results, 1);
                result = true;
            } catch (Exception e) {
                HashMap<String, String> results = new HashMap<String, String> ();
                results.put("URL", link);
                results.put("Exception", e.getMessage());
                Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_FAILED, results, 1);
                result = false;
                mTask.cancel(true);
                mProgressDialog.dismiss();
                // show error message if an error occurs while connecting to the
                // resource
                final PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        IntentController intent = new IntentController(
                                DaisyReaderDownloadBooks.this);
                        ex.showDialogDowloadException(intent);
                    }
                });
            }
            return result;
        }


        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);
            mProgressDialog.setSecondaryProgress(values[1]);
        }

        /**
         * After completing background task Dismiss the progress dialogs
         * **/
        @Override
        protected void onPostExecute(Boolean result) {
            if (alertDialog != null) {
                alertDialog.dismiss();
            }
            mProgressDialog.dismiss();
            try {
                if (result) {
                    DaisyBook daisyBook = new DaisyBook();
                    String path = PATH + mName;
                    if (DaisyBookUtil.findDaisyFormat(path) == Constants.DAISY_202_FORMAT) {
                        daisyBook = DaisyBookUtil.getDaisy202Book(path);
                    } else {
                        daisyBook = DaisyBookUtil.getDaisy30Book(path);
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                ex.writeLogException();
            }
        }
    }

    /**
     * Format date or return empty string.
     * 
     * @param date the date
     * @return the string
     */
    private String formatDateOrReturnEmptyString(Date date) {
        String sDate = "";
        if (date != null) {
            if (Locale.getDefault().getLanguage().equals("ja")) {
                sDate = String.format(Locale.getDefault(), ("%tY/%tm/%td %n"), date, date, date);
            } else {
                sDate = String.format(Locale.getDefault(), ("%tB %te, %tY %n"), date, date, date);
            }
        }
        return sDate;
    }

    private List<String> parseHtml() throws IOException {
        List<String> links = new ArrayList<String>();
        //内閣府 政府広報
        if (mDaisyBook.getPath().indexOf("www.gov-online.go.jp") >= 0) {
            Document document = Jsoup.parse(new FileInputStream(PATH + mName), "UTF-8", PATH);
            Elements elements = document.select("tbody > tr");
            for (org.jsoup.nodes.Element element : elements) {
                links.add(element.select("td > strong").text());
                links.add(element.select("td > a").get(0).attr("href"));
            }
            //東京都
        } else if (mDaisyBook.getPath().indexOf("www.koho.metro.tokyo.jp") >= 0) {
            String split[] = mDaisyBook.getPath().split("/");
            Document document = Jsoup.parse(new FileInputStream(PATH + mName), "Shift_JIS", PATH);
            Elements elements = document.select("div[id=content] table tr");
            for (org.jsoup.nodes.Element element : elements) {
                links.add(element.select("td > a").text());
                links.add(mDaisyBook.getPath().substring(0, mDaisyBook.getPath().length() - split[split.length - 1].length()) + element.select("td > a").attr("href"));
            }
            //大田区
        } else if (mDaisyBook.getPath().indexOf("www.city.ota.tokyo.jp") >= 0) {
            String split[] = mDaisyBook.getPath().split("/");
            Document document = Jsoup.parse(new FileInputStream(PATH + mName), "Shift_JIS", PATH);
            Elements elements = document.select("tbody > tr");
            for (org.jsoup.nodes.Element element : elements) {
                links.add(element.select("a").text());
                links.add("http://www.city.ota.tokyo.jp" + element.select("a").attr("href"));
            }
            //杉並区
        } else if (mDaisyBook.getPath().indexOf("www.city.suginami.tokyo.jp") >= 0) {
            String split[] = mDaisyBook.getPath().split("/");
            Document document = Jsoup.parse(new FileInputStream(PATH + mName), "UTF-8", PATH);
            Elements elements = document.select("li.mp3");
            for (org.jsoup.nodes.Element element : elements) {
                links.add(element.select("a").text());
                links.add(mDaisyBook.getPath().substring(0, mDaisyBook.getPath().length() - split[split.length - 1].length()) + element.select("a").attr("href"));
            }
            //八王子市
        } else if (mDaisyBook.getPath().indexOf("www.city.hachioji.tokyo.jp") >= 0) {
            String split[] = mDaisyBook.getPath().split("/");
            Document document = Jsoup.parse(new FileInputStream(PATH + mName), "UTF-8", PATH);
            Elements elements = document.select("div[id=content] li");
            for (org.jsoup.nodes.Element element : elements) {
                links.add(element.select("a").text());
                links.add(element.select("a").attr("href"));
            }
            //小平市
        } else if (mDaisyBook.getPath().indexOf("www.city.kodaira.tokyo.jp") >= 0) {
            String split[] = mDaisyBook.getPath().split("/");
            Document document = Jsoup.parse(new FileInputStream(PATH + mName), "UTF-8", PATH);
            Elements elements = document.select("div.mp3 li");
            for (org.jsoup.nodes.Element element : elements) {
                links.add(element.select("a").text());
                links.add(mDaisyBook.getPath().substring(0, mDaisyBook.getPath().length() - split[split.length - 1].length()) + element.select("a").attr("href"));
            }
        }
        return links;
    }

    private String getNcc(List<String> lists, Map<String, String> metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        sb.append("<head>\n");
        for(Map.Entry<String, String> e : metadata.entrySet()) {
            sb.append("    <meta name=\"" + e.getKey() + "\" content=\"" + e.getValue() + "\" />\n");
        }
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<h1 class=\"title\" id=\"file0001\"><a href=\"file0001.smil#file0001\">" + metadata.get("dc:title") + "</a></h1>\n");
        for (int i = 2; i < lists.size(); i++) {
            if (lists.get(i).endsWith(".mp3")) {
                sb.append(String.format("<h2 id=\"file%1$04d\"><a href=\"file%1$04d.smil#file%1$04d\">", i) + lists.get(i - 1) + "</a></h2>\n");
            }
        }
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private String getSmil(int no, Map<String, String> metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!DOCTYPE smil PUBLIC \"-//W3C//DTD SMIL 1.0//EN\" \"http://www.w3.org/TR/REC-smil/SMIL10.dtd\">\n");
        sb.append("<smil>\n");
        sb.append("<head>\n");
        for(Map.Entry<String, String> e : metadata.entrySet()) {
            if (e.getKey().equals("dc:title")) {
                sb.append("    <meta name=\"title\" content=\"" + e.getValue() + "\" />\n");
                sb.append("    <meta name=\"" + e.getKey() + "\" content=\"" + e.getValue() + "\" />\n");
            }
            if (e.getKey().equals("ncc:generator") || e.getKey().equals("dc:format") || e.getKey().equals("ncc:timeInThisSmil")) {
                sb.append("    <meta name=\"" + e.getKey() + "\" content=\"" + e.getValue() + "\" />\n");
            }
        }
        sb.append("<layout>\n");
        sb.append("<region id=\"txtView\" />\n");
        sb.append("</layout>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<seq>\n");
        sb.append("<par endsync=\"last\">\n");
        sb.append("<seq>\n");
        sb.append(String.format("<audio src=\"file%1$04d.mp3\" clip-begin=\"0.000s\" clip-end=\"0.000s\" id=\"file%1$04d\" />\n", no));
        sb.append("</seq>\n");
        sb.append("</par>\n");
        sb.append("</seq>\n");
        sb.append("</body>\n");
        sb.append("</smil>\n");
        return sb.toString();
    }

    @Override
    public void onBackPressed() {
        if (mTask != null) {
            mTask.cancel(false);
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleSearchBook();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Show a dialog to confirm exit download.
     * 
     * @param message
     */
    private void pushToDialogOptions(String message) {
        alertDialog = new AlertDialog.Builder(DaisyReaderDownloadBooks.this).create();
        // Setting Dialog Title
        alertDialog.setTitle(R.string.error_title);
        // Setting Dialog Message
        alertDialog.setMessage(message);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        // Setting Icon to Dialog
        alertDialog.setIcon(R.drawable.error);
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                DaisyReaderDownloadBooks.this.getString(R.string.no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mProgressDialog.show();
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,
                DaisyReaderDownloadBooks.this.getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mTask.cancel(true);
                    }
                });
        alertDialog.show();
    }

    private void downloadABook(int position) {
        boolean isConnected = DaisyBookUtil.getConnectivityStatus(DaisyReaderDownloadBooks.this) != Constants.CONNECT_TYPE_NOT_CONNECTED;
        IntentController intent = new IntentController(DaisyReaderDownloadBooks.this);
        if (isConnected) {
            if (checkFolderIsExist()) {
                mDaisyBook = mlistDaisyBook.get(position);
                String link = mDaisyBook.getPath();

                if (checkStorage(link) != 0) {
                    String params[] = { link };
                    runAsynTask(params);
                } else {
                    intent.pushToDialog(DaisyReaderDownloadBooks.this
                            .getString(R.string.error_not_enough_space),
                            DaisyReaderDownloadBooks.this.getString(R.string.error_title),
                            R.raw.error, false, false, null);
                }
            }
        } else {
            intent.pushToDialog(
                    DaisyReaderDownloadBooks.this.getString(R.string.error_connect_internet),
                    DaisyReaderDownloadBooks.this.getString(R.string.error_title), R.raw.error,
                    false, false, null);
        }
    }

    private void add(List<String> list, String str) {
        if (str.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(str)) {
                return;
            }
        }
        list.add(str);
    }

    private String getName(String str) {
        String split[] = str.split("/");
        return split[split.length - 1];
    }

    private String removeS(String str) {
        if (str.indexOf("#") == -1) {
            return str;
        }
        return str.substring(0, str.indexOf("#"));
    }
}
