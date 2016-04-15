package com.github.naofum.daisydownloader.apps;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.SubMenu;
import com.github.naofum.daisydownloader.adapter.WebsiteAdapter;
import com.github.naofum.daisydownloader.base.DaisyEbookReaderBaseActivity;
import com.github.naofum.daisydownloader.metadata.MetaDataHandler;
import com.github.naofum.daisydownloader.player.IntentController;
import com.github.naofum.daisydownloader.utils.Constants;
import org.androiddaisyreader.model.Website;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.actionbarsherlock.view.MenuItem;
import com.github.naofum.daisydownloader.R;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The Class DaisyReaderDownloadSiteActivity.
 */
public class DaisyReaderDownloadSiteActivity extends DaisyEbookReaderBaseActivity {

    private IntentController mIntentController;
    private ListView mListViewWebsite;
    private List<Website> listWebsite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIntentController = new IntentController(this);

        setContentView(R.layout.activity_download_site);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.download_sites);

        mListViewWebsite = (ListView) findViewById(R.id.list_view_website);
        initListWebsite();
        WebsiteAdapter websiteAdapter = new WebsiteAdapter(listWebsite, getLayoutInflater());
        mListViewWebsite.setAdapter(websiteAdapter);

        // set listener while touch on website
        mListViewWebsite.setOnItemClickListener(onItemWebsiteClick);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int order = 1;
        SubMenu subMenu = menu.addSubMenu(0, Constants.SUBMENU_MENU, order++, R.string.menu_title);
        subMenu.add(0, Constants.SUBMENU_SETTINGS, order++, R.string.submenu_settings).setIcon(
                R.raw.settings);
        subMenu.add(0, Constants.SUBMENU_ABOUT, order++, R.string.submenu_about);

        MenuItem subMenuItem = subMenu.getItem();
        subMenuItem.setIcon(R.raw.ic_menu_32x32);
        subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//        case android.R.id.home:
//            backToTopScreen();
//            break;
//        default:
//            return super.onOptionsItemSelected(item);
//        }
//        return false;
        switch (item.getItemId()) {
            // go to settings
            case Constants.SUBMENU_SETTINGS:
                mIntentController.pushToDaisyReaderSettingIntent();
                return true;
            case Constants.SUBMENU_ABOUT:
                new AlertDialog.Builder(DaisyReaderDownloadSiteActivity.this)
                        .setTitle(R.string.submenu_about)
                        .setMessage(getText(R.string.app_name) + "\nVersion: 1.0" + "\nLicense: GPLv3")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private OnItemClickListener onItemWebsiteClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            Website website = listWebsite.get(arg2);
            String websiteUrl = website.getSiteURL();
            String websiteName = website.getSiteName();
            pushToWebsite(websiteUrl, websiteName);
        }
    };

    /**
     * Inits the list website.
     */
    private void initListWebsite() {
        listWebsite = new ArrayList<Website>();
        Website website = null;
        NodeList nList = null;
        try {
            AssetManager assetManager = getAssets();
            InputStream databaseInputStream = assetManager.open(Constants.META_DATA_FILE_NAME);
            MetaDataHandler metadata = new MetaDataHandler();

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc;
            doc = dBuilder.parse(databaseInputStream);
            doc.getDocumentElement().normalize();
            nList = doc.getElementsByTagName(Constants.ATT_WEBSITE);
            for (int i = 0; i < nList.getLength(); i++) {
                Element e = (Element) nList.item(i);
                String urlWebsite = e.getAttribute(Constants.ATT_URL);
                String websiteName = e.getAttribute(Constants.ATT_NAME);
//                String websiteName = getString(getResources().getIdentifier(urlWebsite, "string", getPackageName()));
                website = new Website(websiteName, urlWebsite, i + 1);
                listWebsite.add(website);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadSiteActivity.this);
            ex.writeLogException();
        }
    }

    /**
     * Push to list book of website.
     */
    private void pushToWebsite(String websiteURL, String websiteName) {
        Intent intent = new Intent(this, DaisyReaderDownloadBooks.class);
        intent.putExtra(Constants.LINK_WEBSITE, websiteURL);
        intent.putExtra(Constants.NAME_WEBSITE, websiteName);
        this.startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
