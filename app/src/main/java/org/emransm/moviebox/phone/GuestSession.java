package org.emransm.moviebox.phone;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Only this independent client's guest session. Never reads the original app or a user account. */
final class GuestSession implements Api.NativeSession {
 private static final String KEY="MovieBoxGuestSessionV1";
 private static final Object LOCK=new Object();
 private static final long MAX_AGE=30L*60*1000;
 private final Context context;private final String identity,storeName;
 GuestSession(Context context,String identity){this(context,identity,"moviebox-guest-session");}
 GuestSession(Context context,String identity,String storeName){this.context=context.getApplicationContext();this.identity=identity;this.storeName=storeName;}
 private SecretKey key(boolean create)throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
  if(!create)return null;
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
 }
 public String load(){synchronized(LOCK){try{
  String encoded=context.getSharedPreferences(storeName,Context.MODE_PRIVATE).getString("encrypted","");if(encoded.isEmpty())return "";
  JSONObject envelope=new JSONObject(encoded);SecretKey secret=key(false);if(secret==null)return "";
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secret,new GCMParameterSpec(128,Base64.decode(envelope.getString("iv"),Base64.NO_WRAP)));cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
  JSONObject saved=new JSONObject(new String(cipher.doFinal(Base64.decode(envelope.getString("data"),Base64.NO_WRAP)),StandardCharsets.UTF_8));long age=System.currentTimeMillis()-saved.getLong("at");String token=saved.getString("token");return age>=0&&age<MAX_AGE&&token.length()<=16384?token:"";
 }catch(Exception unavailable){return "";}}}
 public void save(String token){if(token==null||token.isEmpty()||token.length()>16384)return;synchronized(LOCK){try{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
  byte[] bytes=new JSONObject().put("at",System.currentTimeMillis()).put("token",token).toString().getBytes(StandardCharsets.UTF_8);
  String envelope=new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(bytes),Base64.NO_WRAP)).toString();
  context.getSharedPreferences(storeName,Context.MODE_PRIVATE).edit().putString("encrypted",envelope).commit();
 }catch(Exception optional){/* Unsupported keystore/storage falls back to an ordinary guest bootstrap. */}}}
 public void clear(){try{context.getSharedPreferences(storeName,Context.MODE_PRIVATE).edit().remove("encrypted").commit();}catch(RuntimeException optional){/* Retry a fresh guest without making storage essential. */}}
}
