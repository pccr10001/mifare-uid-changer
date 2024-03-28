package li.power.app.mifareuidchanger;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.*;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import li.power.app.mifareuidchanger.databinding.ActivityScrollingBinding;
import li.power.app.mifareuidchanger.model.Settings;
import li.power.app.mifareuidchanger.model.UidItem;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.w3c.dom.Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private ActivityScrollingBinding binding;

    UidAdapter uidAdapter;
    RecyclerView recyclerView;

    private NfcAdapter nfcAdapter;

    private PendingIntent pendingIntent;
    private IntentFilter[] intentFilters;
    private String[][] techList;
    private MifareClassic mfc;

    private AlertDialog addTagUidDialog;

    private AlertDialog writingDialog;
    private View writingDialogView;
    private View dialogView;
    private String scannedUid;

    private static final int REQUEST_SIGNIN_CODE = 400;
    private static final int REQUEST_CREATE_FILE = 401;
    private static final int REQUEST_OPEN_FILE = 402;

    private UidItem toWriteUid = null;


    private GoogleSignInClient googleSignInClient;

    private Drive drive;

    private Settings settings;

    boolean pendingBackup = false;
    boolean pendingRestore = false;
    String backupDestination = "";

    public static final String BACKUP_DESTINATION_GDRIVE = "gdrive";

    private final int targetSector = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityScrollingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = binding.toolbarLayout;
        toolBarLayout.setTitle(getTitle());

        recyclerView = findViewById(R.id.rv_uid_list);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this,
                DividerItemDecoration.VERTICAL));

        settings = getSettings();
        if (settings == null) {
            showWelcomeDialog();
            settings = new Settings();
            saveSettings("FFFFFFFFFFFF", "FFFFFFFFFFFF");
        }

        uidAdapter = new UidAdapter(settings.getList());
        recyclerView.setAdapter(uidAdapter);

        uidAdapter.setOnLongClickListener((position, item) -> {
            showDeleteTagUidDialog(item, position);
        });

        uidAdapter.setOnClickListener((position, item) -> {
            showWritingUidDialog(item);
        });

        FloatingActionButton fab = binding.fab;
        fab.setOnClickListener(view -> showAddTagDialog());
    }

    private void showWelcomeDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Welcome");
        TextView tvMsg = new TextView(getApplicationContext());
        tvMsg.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvMsg.setTextSize(16);
        tvMsg.setGravity(Gravity.CENTER_VERTICAL);
        tvMsg.setPadding(20, 20, 20, 20);
        tvMsg.setText("Press the item to write to the magic tag.\n\nLong press the item to delete the UID.\n\nYou can scan the tag that you want to copy during this application opened,\nthen press + button to add new UID to list.");
        LinearLayout layout = new LinearLayout(getApplicationContext());
        layout.addView(tvMsg);
        alertDialogBuilder.setView(layout);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setNegativeButton("OK", (dialog, which) -> {
            dialog.dismiss();
        });
        alertDialogBuilder.create().show();
    }

    private void showWritingUidDialog(UidItem item) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(String.format("Writing %s (%s) ...", item.getId(), item.getName()));

        LayoutInflater inflater = getLayoutInflater();
        writingDialogView = inflater.inflate(R.layout.writing_layout, null);
        alertDialogBuilder.setView(writingDialogView);
        alertDialogBuilder.setCancelable(true);
        TextView tvMsg = writingDialogView.findViewById(R.id.writing_status);
        tvMsg.setText("Waiting for tag");

        alertDialogBuilder.setNegativeButton("CANCEL", (dialog, which) -> {
            dialog.dismiss();
        });

        alertDialogBuilder.setOnCancelListener(dialog -> {
            writingDialog = null;
            writingDialogView = null;
            toWriteUid = null;
        });

        alertDialogBuilder.setOnDismissListener(dialog -> {
            writingDialog = null;
            writingDialogView = null;
            toWriteUid = null;
        });

        toWriteUid = item;
        writingDialog = alertDialogBuilder.create();
        writingDialog.show();

    }

    private void showDeleteTagUidDialog(UidItem item, int position) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Delete ?");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView tvMsg = new TextView(getApplicationContext());
        tvMsg.setText(String.format("Do you want to delete UID: %s (%s) ?", item.getId(), item.getName()));
        tvMsg.setPadding(16, 16, 16, 16);
        layout.addView(tvMsg);
        alertDialogBuilder.setView(layout);
        alertDialogBuilder.setPositiveButton("YES", (dialog, which) -> {
            settings.getList().remove(position);
            saveList();
            uidAdapter.notifyItemRemoved(position);

        });

        alertDialogBuilder.setNegativeButton("NO", (dialog, which) -> dialog.dismiss());

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Settings");

        LayoutInflater inflater = getLayoutInflater();
        dialogView = inflater.inflate(R.layout.settings_layout, null);
        alertDialogBuilder.setView(dialogView);

        EditText keyAEditText = dialogView.findViewById(R.id.edit_text_key_a);
        EditText keyBEditText = dialogView.findViewById(R.id.edit_text_key_b);
        Settings settings = getSettings();
        if (settings != null) {
            keyAEditText.setText(settings.getKeyA());
            keyBEditText.setText(settings.getKeyB());
        }

        alertDialogBuilder.setPositiveButton("SAVE", null);
        alertDialogBuilder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                dialogView = null;
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.setOnShowListener(dialogInterface -> {
            Button button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String keyA = keyAEditText.getText().toString().trim();
                String keyB = keyBEditText.getText().toString().trim();

                boolean isValidKeyA = isValidHex(keyA) && keyA.length() == 12;
                boolean isValidKeyB = isValidHex(keyB) && keyB.length() == 12;

                if (!isValidKeyA) {
                    keyAEditText.setError("Please input valid HEX string (6 bytes)");

                } else {
                    keyAEditText.setError(null);
                }

                if (!isValidKeyB) {
                    keyBEditText.setError("Please input valid HEX string (6 bytes)");
                } else {
                    keyBEditText.setError(null);
                }

                if (isValidKeyA && isValidKeyB) {
                    saveSettings(keyA, keyB);
                    alertDialog.dismiss();
                    dialogView = null;
                }

            });
        });
        alertDialog.show();
    }

    private void showAddTagDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Add UID");


        LayoutInflater inflater = getLayoutInflater();
        dialogView = inflater.inflate(R.layout.add_tag_layout, null);
        alertDialogBuilder.setView(dialogView);

        EditText uidEditText = dialogView.findViewById(R.id.edit_text_default_uid);
        EditText uidNameEditText = dialogView.findViewById(R.id.edit_text_default_uid_name);

        if (scannedUid != null && !scannedUid.equals("")) {
            uidEditText.setText(scannedUid);
        }

        alertDialogBuilder.setPositiveButton("SAVE", null);
        alertDialogBuilder.setNegativeButton("CANCEL", (dialog, which) -> {
            dialog.dismiss();
            addTagUidDialog = null;
            dialogView = null;
        });

        addTagUidDialog = alertDialogBuilder.create();
        addTagUidDialog.setOnShowListener(dialogInterface -> {
            Button button = addTagUidDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {

                String uid = uidEditText.getText().toString().trim();

                boolean isValidUid = isValidHex(uid) && uid.length() == 8;

                if (!isValidUid) {
                    uidEditText.setError("Please input valid HEX string (4 bytes)");
                } else {
                    uidEditText.setError(null);
                }

                if (isValidUid) {
                    saveTagUid(uid, uidNameEditText.getText().toString().trim());
                    addTagUidDialog.dismiss();
                    addTagUidDialog = null;
                    dialogView = null;
                }

            });
        });
        addTagUidDialog.show();
    }

    private boolean isValidHex(String hexString) {
        return hexString.matches("[0-9A-Fa-f]+");
    }

    private void saveSettings(String keyA, String keyB) {
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        settings.setKeyA(keyA);
        settings.setKeyB(keyB);
        sp.edit().putString("settings", new Gson().toJson(settings)).apply();
    }

    private void saveList() {
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        sp.edit().putString("settings", new Gson().toJson(settings)).apply();
    }

    private Settings getSettings() {
        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        String settings = sp.getString("settings", "");
        if (settings.equals("")) {
            return null;
        }
        return new Gson().fromJson(settings, Settings.class);
    }

    private void saveTagUid(String uid, String name) {
        boolean exists = false;
        if(name.isEmpty()){
            name="Tag "+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-hhmmss"));
        }
        for (int i = 0; i < settings.getList().size(); i++) {
            if (uid.equals(settings.getList().get(i).getId())) {
                settings.getList().get(i).setName(name);
                exists = true;
                break;
            }
        }
        if (!exists) {
            settings.getList().add(new UidItem(uid, name));
        }

        uidAdapter.notifyDataSetChanged();
        saveList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            showSettingsDialog();
        } else if (id == R.id.action_backup_gdrive) {
            backupToGDrive();
        } else if (id == R.id.action_backup_local) {
            backupToLocal();
        } else if (id == R.id.action_restore_gdrive) {
            restoreFromGDrive();
        } else if (id == R.id.action_restore_local) {
            restoreFromLocal();
        }
        return super.onOptionsItemSelected(item);
    }

    private void backupToGDrive() {
        if (drive == null) {
            requestSignIn();
            pendingBackup = true;
            backupDestination = BACKUP_DESTINATION_GDRIVE;
            return;
        }

        File fileMetaData = new File();
        fileMetaData.setName("settings.json");
        String settingsStr = new Gson().toJson(settings);
        new Thread(() -> {
            try {
                drive.files().create(fileMetaData,
                                new InputStreamContent("application/octet-stream",
                                        new ByteArrayInputStream(settingsStr.getBytes())))
                        .execute();
                showToast("Backup uploaded");
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Failed to upload to Google Drive, " + e.toString());
            }
        }).start();

    }

    private void backupToLocal() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "muc_settings.json");

        startActivityForResult(intent, REQUEST_CREATE_FILE);

    }

    private void restoreFromGDrive() {
        if (drive == null) {
            requestSignIn();
            pendingRestore = true;
            backupDestination = BACKUP_DESTINATION_GDRIVE;
            return;
        }
        new Thread(() -> {
            try {
                File file = null;
                FileList list = drive.files().list().execute();
                for (File f : list.getFiles()) {
                    if (f.getName().equals("settings.json")) {
                        file = f;
                        break;
                    }
                }
                if (file == null) {
                    showToast("No backup found in Google Drive");
                    return;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
                drive.files().get(file.getId()).executeMediaAndDownloadTo(baos);
                Settings newSettings = new Gson().fromJson(baos.toString(), Settings.class);
                baos.close();
                updateSettings(newSettings);
                runOnUiThread(() -> {
                    saveList();
                    uidAdapter.notifyDataSetChanged();
                });

                showToast("Settings restored");
            } catch (Exception e) {
                showToast("Failed to restore from Google Drive, " + e.toString());
                e.printStackTrace();
            }
        }).start();
    }

    private void updateSettings(Settings settings) {
        this.settings.setKeyA(settings.getKeyA());
        this.settings.setKeyB(settings.getKeyB());
        this.settings.getList().clear();
        this.settings.getList().addAll(settings.getList());
    }

    private void restoreFromLocal() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "muc_settings.json");

        startActivityForResult(intent, REQUEST_OPEN_FILE);
    }

    private void setupNfc() {
        if (nfcAdapter != null) {
            return;
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            showToast("No NFC supported on this phone");
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            showToast("NFC Adapter is disabled");
            return;
        }

        pendingIntent = PendingIntent.getActivity(this, 1, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE);
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        try {
            filter.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            e.printStackTrace();
        }
        intentFilters = new IntentFilter[]{filter};
        techList = new String[][]{new String[]{NfcA.class.getName()}};
    }

    private void enableForegroundDispatch() {
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techList);
        }
    }

    private void disableForegroundDispatch() {
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    private void handleIntent(Intent intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            NfcA tag = NfcA.get(intent.getParcelableExtra(NfcAdapter.EXTRA_TAG));
            scannedUid = Hex.encodeHexString(tag.getTag().getId()).toUpperCase();

            if(scannedUid.length()> 8){
                showToast("Non-4bytes UID detected, we can't supported it, truncated to 4 bytes");
                scannedUid = scannedUid.substring(0,8);
            }else if(scannedUid.length()<8){
                showToast("Non-4bytes UID detected, we can't supported it");
                return;
            }

            // writing
            if (writingDialogView != null) {
                try {
                    mfc = MifareClassic.get(intent.getParcelableExtra(NfcAdapter.EXTRA_TAG));
                    updateWritingDialog("Connecting to tag");
                    mfc.connect();
                    updateWritingDialog("Authenticating with sector 0");
                    boolean authA = mfc.authenticateSectorWithKeyA(targetSector / 4, Hex.decodeHex(settings.getKeyA()));
                    boolean authB = mfc.authenticateSectorWithKeyB(targetSector / 4, Hex.decodeHex(settings.getKeyB()));
                    if (!authA || !authB) {
                        writingDialog.dismiss();
                        showToast("Failed to authenticate with sector 0 of card, maybe wrong keys?");
                        return;
                    }

                    byte[] uid = Hex.decodeHex(toWriteUid.getId());
                    updateWritingDialog("Reading sector 0");
                    byte[] sector = mfc.readBlock(targetSector);
                    Log.d("MUC", "Previous data: " + Hex.encodeHexString(sector));
                    System.arraycopy(uid, 0, sector, 0, 4);
                    sector[4] = (byte) (sector[0] ^ sector[1] ^ sector[2] ^ sector[3]);
                    Log.d("MUC", "To write: " + Hex.encodeHexString(sector));
                    updateWritingDialog("Writing sector 0");
                    mfc.writeBlock(targetSector, sector);
                    byte[] newSector = mfc.readBlock(targetSector);

                    runOnUiThread(() -> writingDialog.dismiss());

                    for (int i = 0; i < 16; i++) {
                        if (sector.length != newSector.length || sector[i] != newSector[i]) {
                            showToast("Failed to write sector 0, MAGIC may not exist on your card");
                        }
                    }
                    mfc.close();
                    showToast("UID updated");

                } catch (IOException | DecoderException | NullPointerException e) {
                    runOnUiThread(() -> writingDialog.dismiss());
                    showToast("Failed to connect to tag, maybe it is not Mifare Classic tag?");
                }
                return;
            }

            if (dialogView != null) {
                EditText etUid = dialogView.findViewById(R.id.edit_text_default_uid);
                etUid.setText(scannedUid);
            } else {
                showAddTagDialog();
            }

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupNfc();
        enableForegroundDispatch();
    }

    @Override
    protected void onPause() {
        disableForegroundDispatch();
        super.onPause();
    }

    public void requestSignIn() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
        startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_SIGNIN_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGNIN_CODE && resultCode == RESULT_OK) {
            handleSignInIntent(data);
        } else if (requestCode == REQUEST_CREATE_FILE && resultCode == RESULT_OK) {
            OutputStream outputStream = null;
            try {
                if (data == null) {
                    return;
                }

                outputStream = getContentResolver().openOutputStream(data.getData());
                String settingsStr = new Gson().toJson(settings);
                outputStream.write(settingsStr.getBytes());
                outputStream.close();
                showToast("Backup saved");
            } catch (IOException e) {
                showToast("Failed to write backup");
            }

        } else if (requestCode == REQUEST_OPEN_FILE && resultCode == RESULT_OK) {
            try {
                if (data == null) {
                    return;
                }
                Reader in = new InputStreamReader(getContentResolver().openInputStream(data.getData()), StandardCharsets.UTF_8);
                Settings newSettings = new Gson().fromJson(in, Settings.class);
                in.close();
                updateSettings(newSettings);
                saveList();
                uidAdapter.notifyDataSetChanged();
                showToast("Backup restored");
            } catch (IOException e) {
                showToast("Failed to read backup");
            }
        }
    }

    private void handleSignInIntent(Intent data) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnSuccessListener(googleSignInAccount -> {
                    GoogleAccountCredential credential = GoogleAccountCredential
                            .usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE_FILE));
                    credential.setSelectedAccount(googleSignInAccount.getAccount());
                    initializeDriveClient(credential);
                })
                .addOnFailureListener(e -> e.printStackTrace());
    }

    private void initializeDriveClient(GoogleAccountCredential credential) {
        drive = new Drive.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("Mifare UID Changer")
                .build();
        if (pendingBackup && backupDestination == BACKUP_DESTINATION_GDRIVE) {
            pendingBackup = false;
            backupDestination = "";
            backupToGDrive();
            return;
        }
        if (pendingRestore && backupDestination == BACKUP_DESTINATION_GDRIVE) {
            pendingRestore = false;
            backupDestination = "";
            restoreFromGDrive();
            return;
        }
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private void updateWritingDialog(String msg) {
        runOnUiThread(() -> {
            TextView tvMsg = writingDialogView.findViewById(R.id.writing_status);
            tvMsg.setText(msg);
        });
    }
}