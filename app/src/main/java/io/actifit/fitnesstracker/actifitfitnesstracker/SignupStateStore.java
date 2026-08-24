package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SignupStateStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "actifit_temporary_signup_recovery";
    private static final String FILE_NAME = "signup_recovery.enc";
    private static final int FILE_VERSION = 1;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_IV_BYTES = 32;
    private static final int MAX_CIPHERTEXT_BYTES = 64 * 1024;

    private final AtomicFile stateFile;

    SignupStateStore(Context context) {
        stateFile = new AtomicFile(new File(context.getNoBackupFilesDir(), FILE_NAME));
    }

    boolean exists() {
        try (FileInputStream ignored = stateFile.openRead()) {
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (Exception e) {
            // A present but unreadable recovery must be surfaced and fail closed in load().
            return true;
        }
    }

    SignupState load() throws SignupStateStoreException {
        if (!exists()) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(stateFile.openRead())) {
            if (input.readInt() != FILE_VERSION) {
                throw new SignupStateStoreException("Unsupported signup recovery version");
            }
            int ivLength = input.readInt();
            if (ivLength <= 0 || ivLength > MAX_IV_BYTES) {
                throw new SignupStateStoreException("Invalid signup recovery IV");
            }
            byte[] iv = new byte[ivLength];
            input.readFully(iv);
            int ciphertextLength = input.readInt();
            if (ciphertextLength <= 0 || ciphertextLength > MAX_CIPHERTEXT_BYTES) {
                throw new SignupStateStoreException("Invalid signup recovery payload");
            }
            byte[] ciphertext = new byte[ciphertextLength];
            input.readFully(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            try {
                return SignupState.fromJson(
                        new JSONObject(new String(plaintext, StandardCharsets.UTF_8)));
            } finally {
                java.util.Arrays.fill(plaintext, (byte) 0);
            }
        } catch (SignupStateStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new SignupStateStoreException("Unable to read signup recovery state", e);
        }
    }

    void save(SignupState state) throws SignupStateStoreException {
        FileOutputStream output = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] plaintext = state.toJson().toString().getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext;
            try {
                ciphertext = cipher.doFinal(plaintext);
            } finally {
                java.util.Arrays.fill(plaintext, (byte) 0);
            }

            output = stateFile.startWrite();
            DataOutputStream data = new DataOutputStream(output);
            byte[] iv = cipher.getIV();
            data.writeInt(FILE_VERSION);
            data.writeInt(iv.length);
            data.write(iv);
            data.writeInt(ciphertext.length);
            data.write(ciphertext);
            data.flush();
            stateFile.finishWrite(output);
            output = null;
        } catch (Exception e) {
            if (output != null) {
                stateFile.failWrite(output);
            }
            throw new SignupStateStoreException("Unable to save signup recovery state", e);
        }
    }

    void clear() throws SignupStateStoreException {
        try {
            stateFile.delete();
            if (stateFile.getBaseFile().exists()) {
                throw new SignupStateStoreException("Signup recovery file could not be deleted");
            }
            KeyStore keyStore = loadKeyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception e) {
            throw new SignupStateStoreException("Unable to clear signup recovery state", e);
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private SecretKey getExistingKey() throws Exception {
        KeyStore keyStore = loadKeyStore();
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            throw new SignupStateStoreException("Signup recovery key is unavailable");
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    private KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    static final class SignupStateStoreException extends Exception {
        SignupStateStoreException(String message) {
            super(message);
        }

        SignupStateStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
