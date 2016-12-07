package com.rt2zz.reactnativecontacts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
//import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static android.provider.ContactsContract.CommonDataKinds.Contactables;
import static android.provider.ContactsContract.CommonDataKinds.Email;
import static android.provider.ContactsContract.CommonDataKinds.Phone;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static android.provider.ContactsContract.CommonDataKinds.Organization;

public class ContactsProvider {
    public static final int ID_FOR_PROFILE_CONTACT = -1;

    private static final List<String> PROJECTION_ID_NAME_IMG = new ArrayList<String>() {{
        add(ContactsContract.Contacts.Data.MIMETYPE);
        add(ContactsContract.Profile.DISPLAY_NAME);
        add(Contactables.PHOTO_URI);
        add(StructuredName.DISPLAY_NAME);
        add(ContactsContract.Data.CONTACT_ID);
    }};

    private static final List<String> PROJECTION_ID_NAME_IMG_PHONENUMBERS_EMAILS = new ArrayList<String>() {{
        add(ContactsContract.Contacts.Data.MIMETYPE);
        add(ContactsContract.Profile.DISPLAY_NAME);
        add(Contactables.PHOTO_URI);
        add(StructuredName.DISPLAY_NAME);
        add(ContactsContract.Data.CONTACT_ID);
        add(ContactsContract.CommonDataKinds.Phone.NUMBER);
        add(Phone.NUMBER);
        add(Phone.TYPE);
        add(Email.ADDRESS);
        //add(ContactsContract.RawContacts.SOURCE_ID);
    }};

    private final ContentResolver contentResolver;
    private final Context context;

    public ContactsProvider(ContentResolver contentResolver, Context context) {
        this.contentResolver = contentResolver;
        this.context = context;
    }

    public WritableArray getContacts() {
        Map<String, Contact_Id_Name_Img> phonebookContacts;
        {
            Cursor cursor = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    PROJECTION_ID_NAME_IMG.toArray(new String[PROJECTION_ID_NAME_IMG.size()]),
                    ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{StructuredName.CONTENT_ITEM_TYPE},
                    null
            );

            try {
                phonebookContacts = loadContactsFrom_Id_Name_Img(cursor);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        WritableArray contacts = Arguments.createArray();
        for (Contact_Id_Name_Img contact : phonebookContacts.values()) {
            contacts.pushMap(contact.toMap_Id_Name_Img());
        }

        return contacts;
    }

    @NonNull
    private Map<String, Contact_Id_Name_Img> loadContactsFrom_Id_Name_Img(Cursor cursor) {

        Map<String, Contact_Id_Name_Img> map = new LinkedHashMap<>();

        while (cursor != null && cursor.moveToNext()) {

            int columnIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
            String contactId;
            if (columnIndex != -1) {
                contactId = String.valueOf(cursor.getInt(columnIndex));
            } else {
                contactId = String.valueOf(ID_FOR_PROFILE_CONTACT);
            }

            columnIndex = cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID);
            if (columnIndex != -1) {
                String uid = cursor.getString(columnIndex);
                if (uid != null) {
                    contactId = uid;
                }
            }

            if (!map.containsKey(contactId)) {
                map.put(contactId, new Contact_Id_Name_Img(contactId));
            }

            Contact_Id_Name_Img contact = map.get(contactId);

            String mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));

            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            if (!TextUtils.isEmpty(name) && TextUtils.isEmpty(contact.displayName)) {
                contact.displayName = name;
            }

            String rawPhotoURI = cursor.getString(cursor.getColumnIndex(Contactables.PHOTO_URI));
            if (!TextUtils.isEmpty(rawPhotoURI)) {
                contact.photoUri = getPhotoURIFromContactURI(rawPhotoURI, contactId);
            }
        }

        return map;
    }

    @NonNull
    private Map<String, Contact_Id_Name_Img_PhoneNumbers_Emails> loadContactsFrom_Id_Name_Img_PhoneNumbers_Emails(Cursor cursor) {

        Map<String, Contact_Id_Name_Img_PhoneNumbers_Emails> map = new LinkedHashMap<>();

        while (cursor != null && cursor.moveToNext()) {

            int columnIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
            String contactId;
            if (columnIndex != -1) {
                contactId = String.valueOf(cursor.getInt(columnIndex));
            } else {
                contactId = String.valueOf(ID_FOR_PROFILE_CONTACT);//no contact id for 'ME' user
            }

            columnIndex = cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID);
            if (columnIndex != -1) {
                String uid = cursor.getString(columnIndex);
                if (uid != null) {
                    contactId = uid;
                }
            }

            if (!map.containsKey(contactId)) {
                map.put(contactId, new Contact_Id_Name_Img_PhoneNumbers_Emails(contactId));
            }

            Contact_Id_Name_Img_PhoneNumbers_Emails contact = map.get(contactId);

            String mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));

            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            if (!TextUtils.isEmpty(name) && TextUtils.isEmpty(contact.displayName)) {
                contact.displayName = name;
            }

            String rawPhotoURI = cursor.getString(cursor.getColumnIndex(Contactables.PHOTO_URI));
            if (!TextUtils.isEmpty(rawPhotoURI)) {
                contact.photoUri = getPhotoURIFromContactURI(rawPhotoURI, contactId);
            }

            if (mimeType.equals(StructuredName.CONTENT_ITEM_TYPE)) {
                contact.givenName = cursor.getString(cursor.getColumnIndex(StructuredName.GIVEN_NAME));
            } else if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
                String phoneNumber = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                int type = cursor.getInt(cursor.getColumnIndex(Phone.TYPE));

                if (!TextUtils.isEmpty(phoneNumber)) {
                    String label;
                    switch (type) {
                        case Phone.TYPE_HOME:
                            label = "home";
                            break;
                        case Phone.TYPE_WORK:
                            label = "work";
                            break;
                        case Phone.TYPE_MOBILE:
                            label = "mobile";
                            break;
                        default:
                            label = "other";
                    }
                    contact.phones.add(new Contact_Id_Name_Img_PhoneNumbers_Emails.Item(label, phoneNumber));
                }
            } else if (mimeType.equals(Email.CONTENT_ITEM_TYPE)) {
                String email = cursor.getString(cursor.getColumnIndex(Email.ADDRESS));
                int type = cursor.getInt(cursor.getColumnIndex(Email.TYPE));

                if (!TextUtils.isEmpty(email)) {
                    String label;
                    switch (type) {
                        case Email.TYPE_HOME:
                            label = "home";
                            break;
                        case Email.TYPE_WORK:
                            label = "work";
                            break;
                        case Email.TYPE_MOBILE:
                            label = "mobile";
                            break;
                        case Email.TYPE_CUSTOM:
                            if (cursor.getString(cursor.getColumnIndex(Email.LABEL)) != null) {
                                label = cursor.getString(cursor.getColumnIndex(Email.LABEL)).toLowerCase();
                            } else {
                                label = "";
                            }
                            break;
                        default:
                            label = "other";
                    }
                    contact.emails.add(new Contact_Id_Name_Img_PhoneNumbers_Emails.Item(label, email));
                }
            }
        }

        return map;
    }

    private String getPhotoURIFromContactURI(String contactURIString, String contactId) {
        Uri contactURI = Uri.parse(contactURIString);

        try {
            InputStream photoStream = contentResolver.openInputStream(contactURI);

            if (photoStream == null)
                return "";

            try {
                BufferedInputStream in = new BufferedInputStream(photoStream);
                File outputDir = context.getCacheDir();
                File outputFile = File.createTempFile("contact" + contactId, ".jpg", outputDir);
                FileOutputStream output = new FileOutputStream(outputFile);

                try {
                    int count;
                    byte[] buffer = new byte[4098];

                    while ((count = in.read(buffer)) > 0) {
                        output.write(buffer, 0, count);
                    }
                } catch (IOException e) {
                    output.close();
                }

                in.close();

                return "file://" + outputFile.getAbsolutePath();
            } finally {
                photoStream.close();
            }
        } catch (IOException e) {
            Log.w("RNContacts", "Failed to get photo uri", e);
            return "";
        }
    }

    private static class Contact_Id_Name_Img {
        private String contactId;
        private String displayName;
        private String photoUri;

        public Contact_Id_Name_Img(String contactId) {
            this.contactId = contactId;
        }

        public WritableMap toMap_Id_Name_Img() {
            WritableMap contact = Arguments.createMap();
            contact.putString("recordID", contactId);
            contact.putString("displayName", displayName);
            contact.putString("thumbnailPath", photoUri == null ? "" : photoUri);
            return contact;
        }

        public WritableMap toMap_Id_Name_Img_PhoneNumbers_Emails() {
            WritableMap contact = Arguments.createMap();
            contact.putString("recordID", contactId);
            contact.putString("displayName", displayName);
            contact.putString("thumbnailPath", photoUri == null ? "" : photoUri);

            return contact;
        }

        public static class Item {
            public String label;
            public String value;

            public Item(String label, String value) {
                this.label = label;
                this.value = value;
            }
        }
    }

    private static class Contact_Id_Name_Img_PhoneNumbers_Emails {
        private String contactId;
        private String displayName;
        private String givenName = "";
        private String photoUri;
        private List<Item> emails = new ArrayList<>();
        private List<Item> phones = new ArrayList<>();

        public Contact_Id_Name_Img_PhoneNumbers_Emails(String contactId) {
            this.contactId = contactId;
        }

        public WritableMap toMap_Id_Name_Img_PhoneNumbers_Emails() {
            WritableMap contact = Arguments.createMap();
            contact.putString("recordID", contactId);
            contact.putString("displayName", displayName);
            contact.putString("thumbnailPath", photoUri == null ? "" : photoUri);

            WritableArray phoneNumbers = Arguments.createArray();
            for (Item item : phones) {
                WritableMap map = Arguments.createMap();
                map.putString("number", item.value);
                map.putString("label", item.label);
                phoneNumbers.pushMap(map);
            }
            contact.putArray("phoneNumbers", phoneNumbers);

            WritableArray emailAddresses = Arguments.createArray();
            for (Item item : emails) {
                WritableMap map = Arguments.createMap();
                map.putString("email", item.value);
                map.putString("label", item.label);
                emailAddresses.pushMap(map);
            }
            contact.putArray("emailAddresses", emailAddresses);

            return contact;
        }

        public static class Item {
            public String label;
            public String value;

            public Item(String label, String value) {
                this.label = label;
                this.value = value;
            }
        }
    }

    public WritableArray getFromRecordId(String recordID) {
        Map<String, Contact_Id_Name_Img_PhoneNumbers_Emails> phonebookContacts;
        {

            Cursor cursor = contentResolver.query(ContactsContract.Data.CONTENT_URI,
                    PROJECTION_ID_NAME_IMG_PHONENUMBERS_EMAILS.toArray(new String[PROJECTION_ID_NAME_IMG_PHONENUMBERS_EMAILS.size()]),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{recordID},
                    null);

            try {
                phonebookContacts = loadContactsFrom_Id_Name_Img_PhoneNumbers_Emails(cursor);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        WritableArray contacts = Arguments.createArray();
        for (Contact_Id_Name_Img_PhoneNumbers_Emails contact : phonebookContacts.values()) {
            contacts.pushMap(contact.toMap_Id_Name_Img_PhoneNumbers_Emails());
        }

        return contacts;
    }
}
