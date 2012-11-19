/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\cvs\\alps13low0907\\alps\\packages\\apps\\Music\\src\\com\\mediatek\\bluetooth\\avrcp\\IBTAvrcpMusic.aidl
 */
package com.mediatek.bluetooth.avrcp;
public interface IBTAvrcpMusic extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.mediatek.bluetooth.avrcp.IBTAvrcpMusic
{
private static final java.lang.String DESCRIPTOR = "com.mediatek.bluetooth.avrcp.IBTAvrcpMusic";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.mediatek.bluetooth.avrcp.IBTAvrcpMusic interface,
 * generating a proxy if needed.
 */
public static com.mediatek.bluetooth.avrcp.IBTAvrcpMusic asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.mediatek.bluetooth.avrcp.IBTAvrcpMusic))) {
return ((com.mediatek.bluetooth.avrcp.IBTAvrcpMusic)iin);
}
return new com.mediatek.bluetooth.avrcp.IBTAvrcpMusic.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_registerCallback:
{
data.enforceInterface(DESCRIPTOR);
com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback _arg0;
_arg0 = com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback.Stub.asInterface(data.readStrongBinder());
this.registerCallback(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_unregisterCallback:
{
data.enforceInterface(DESCRIPTOR);
com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback _arg0;
_arg0 = com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback.Stub.asInterface(data.readStrongBinder());
this.unregisterCallback(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_regNotificationEvent:
{
data.enforceInterface(DESCRIPTOR);
byte _arg0;
_arg0 = data.readByte();
int _arg1;
_arg1 = data.readInt();
boolean _result = this.regNotificationEvent(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_setPlayerApplicationSettingValue:
{
data.enforceInterface(DESCRIPTOR);
byte _arg0;
_arg0 = data.readByte();
byte _arg1;
_arg1 = data.readByte();
boolean _result = this.setPlayerApplicationSettingValue(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getCapabilities:
{
data.enforceInterface(DESCRIPTOR);
byte[] _result = this.getCapabilities();
reply.writeNoException();
reply.writeByteArray(_result);
return true;
}
case TRANSACTION_play:
{
data.enforceInterface(DESCRIPTOR);
this.play();
reply.writeNoException();
return true;
}
case TRANSACTION_stop:
{
data.enforceInterface(DESCRIPTOR);
this.stop();
reply.writeNoException();
return true;
}
case TRANSACTION_pause:
{
data.enforceInterface(DESCRIPTOR);
this.pause();
reply.writeNoException();
return true;
}
case TRANSACTION_resume:
{
data.enforceInterface(DESCRIPTOR);
this.resume();
reply.writeNoException();
return true;
}
case TRANSACTION_prev:
{
data.enforceInterface(DESCRIPTOR);
this.prev();
reply.writeNoException();
return true;
}
case TRANSACTION_next:
{
data.enforceInterface(DESCRIPTOR);
this.next();
reply.writeNoException();
return true;
}
case TRANSACTION_prevGroup:
{
data.enforceInterface(DESCRIPTOR);
this.prevGroup();
reply.writeNoException();
return true;
}
case TRANSACTION_nextGroup:
{
data.enforceInterface(DESCRIPTOR);
this.nextGroup();
reply.writeNoException();
return true;
}
case TRANSACTION_setEqualizeMode:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
boolean _result = this.setEqualizeMode(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getEqualizeMode:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getEqualizeMode();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_setShuffleMode:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
boolean _result = this.setShuffleMode(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getShuffleMode:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getShuffleMode();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_setRepeatMode:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
boolean _result = this.setRepeatMode(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getRepeatMode:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getRepeatMode();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_setScanMode:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
boolean _result = this.setScanMode(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getScanMode:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getScanMode();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_informDisplayableCharacterSet:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
boolean _result = this.informDisplayableCharacterSet(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_informBatteryStatusOfCT:
{
data.enforceInterface(DESCRIPTOR);
boolean _result = this.informBatteryStatusOfCT();
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getPlayStatus:
{
data.enforceInterface(DESCRIPTOR);
byte _result = this.getPlayStatus();
reply.writeNoException();
reply.writeByte(_result);
return true;
}
case TRANSACTION_position:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.position();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_duration:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.duration();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_getAudioId:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.getAudioId();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_getTrackName:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getTrackName();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_getAlbumName:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getAlbumName();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_getAlbumId:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.getAlbumId();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_getArtistName:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getArtistName();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_enqueue:
{
data.enforceInterface(DESCRIPTOR);
long[] _arg0;
_arg0 = data.createLongArray();
int _arg1;
_arg1 = data.readInt();
this.enqueue(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_getNowPlaying:
{
data.enforceInterface(DESCRIPTOR);
long[] _result = this.getNowPlaying();
reply.writeNoException();
reply.writeLongArray(_result);
return true;
}
case TRANSACTION_getNowPlayingItemName:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
java.lang.String _result = this.getNowPlayingItemName(_arg0);
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_open:
{
data.enforceInterface(DESCRIPTOR);
long[] _arg0;
_arg0 = data.createLongArray();
int _arg1;
_arg1 = data.readInt();
this.open(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_getQueuePosition:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getQueuePosition();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_setQueuePosition:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setQueuePosition(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.mediatek.bluetooth.avrcp.IBTAvrcpMusic
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public void registerCallback(com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_registerCallback, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void unregisterCallback(com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_unregisterCallback, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public boolean regNotificationEvent(byte eventId, int interval) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeByte(eventId);
_data.writeInt(interval);
mRemote.transact(Stub.TRANSACTION_regNotificationEvent, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean setPlayerApplicationSettingValue(byte attrId, byte value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeByte(attrId);
_data.writeByte(value);
mRemote.transact(Stub.TRANSACTION_setPlayerApplicationSettingValue, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public byte[] getCapabilities() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
byte[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getCapabilities, _data, _reply, 0);
_reply.readException();
_result = _reply.createByteArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void play() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_play, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void stop() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_stop, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void pause() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_pause, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void resume() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_resume, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void prev() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_prev, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void next() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_next, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void prevGroup() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_prevGroup, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void nextGroup() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_nextGroup, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public boolean setEqualizeMode(int equalizeMode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(equalizeMode);
mRemote.transact(Stub.TRANSACTION_setEqualizeMode, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public int getEqualizeMode() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getEqualizeMode, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean setShuffleMode(int shufflemode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(shufflemode);
mRemote.transact(Stub.TRANSACTION_setShuffleMode, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public int getShuffleMode() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getShuffleMode, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean setRepeatMode(int repeatmode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(repeatmode);
mRemote.transact(Stub.TRANSACTION_setRepeatMode, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public int getRepeatMode() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getRepeatMode, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean setScanMode(int scanMode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(scanMode);
mRemote.transact(Stub.TRANSACTION_setScanMode, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public int getScanMode() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getScanMode, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean informDisplayableCharacterSet(int charset) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(charset);
mRemote.transact(Stub.TRANSACTION_informDisplayableCharacterSet, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean informBatteryStatusOfCT() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_informBatteryStatusOfCT, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public byte getPlayStatus() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
byte _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getPlayStatus, _data, _reply, 0);
_reply.readException();
_result = _reply.readByte();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public long position() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_position, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public long duration() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_duration, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public long getAudioId() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getAudioId, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getTrackName() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getTrackName, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getAlbumName() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getAlbumName, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public long getAlbumId() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getAlbumId, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getArtistName() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getArtistName, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void enqueue(long[] list, int action) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLongArray(list);
_data.writeInt(action);
mRemote.transact(Stub.TRANSACTION_enqueue, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public long[] getNowPlaying() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long[] _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getNowPlaying, _data, _reply, 0);
_reply.readException();
_result = _reply.createLongArray();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getNowPlayingItemName(long id) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(id);
mRemote.transact(Stub.TRANSACTION_getNowPlayingItemName, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void open(long[] list, int position) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLongArray(list);
_data.writeInt(position);
mRemote.transact(Stub.TRANSACTION_open, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public int getQueuePosition() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getQueuePosition, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void setQueuePosition(int index) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(index);
mRemote.transact(Stub.TRANSACTION_setQueuePosition, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_registerCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_unregisterCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_regNotificationEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_setPlayerApplicationSettingValue = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_getCapabilities = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_play = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_stop = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_pause = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
static final int TRANSACTION_resume = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
static final int TRANSACTION_prev = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
static final int TRANSACTION_next = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
static final int TRANSACTION_prevGroup = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
static final int TRANSACTION_nextGroup = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
static final int TRANSACTION_setEqualizeMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
static final int TRANSACTION_getEqualizeMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
static final int TRANSACTION_setShuffleMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
static final int TRANSACTION_getShuffleMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
static final int TRANSACTION_setRepeatMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
static final int TRANSACTION_getRepeatMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
static final int TRANSACTION_setScanMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
static final int TRANSACTION_getScanMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
static final int TRANSACTION_informDisplayableCharacterSet = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
static final int TRANSACTION_informBatteryStatusOfCT = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
static final int TRANSACTION_getPlayStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
static final int TRANSACTION_position = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
static final int TRANSACTION_duration = (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);
static final int TRANSACTION_getAudioId = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
static final int TRANSACTION_getTrackName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 27);
static final int TRANSACTION_getAlbumName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 28);
static final int TRANSACTION_getAlbumId = (android.os.IBinder.FIRST_CALL_TRANSACTION + 29);
static final int TRANSACTION_getArtistName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 30);
static final int TRANSACTION_enqueue = (android.os.IBinder.FIRST_CALL_TRANSACTION + 31);
static final int TRANSACTION_getNowPlaying = (android.os.IBinder.FIRST_CALL_TRANSACTION + 32);
static final int TRANSACTION_getNowPlayingItemName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 33);
static final int TRANSACTION_open = (android.os.IBinder.FIRST_CALL_TRANSACTION + 34);
static final int TRANSACTION_getQueuePosition = (android.os.IBinder.FIRST_CALL_TRANSACTION + 35);
static final int TRANSACTION_setQueuePosition = (android.os.IBinder.FIRST_CALL_TRANSACTION + 36);
}
public void registerCallback(com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback callback) throws android.os.RemoteException;
public void unregisterCallback(com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback callback) throws android.os.RemoteException;
public boolean regNotificationEvent(byte eventId, int interval) throws android.os.RemoteException;
public boolean setPlayerApplicationSettingValue(byte attrId, byte value) throws android.os.RemoteException;
public byte[] getCapabilities() throws android.os.RemoteException;
public void play() throws android.os.RemoteException;
public void stop() throws android.os.RemoteException;
public void pause() throws android.os.RemoteException;
public void resume() throws android.os.RemoteException;
public void prev() throws android.os.RemoteException;
public void next() throws android.os.RemoteException;
public void prevGroup() throws android.os.RemoteException;
public void nextGroup() throws android.os.RemoteException;
public boolean setEqualizeMode(int equalizeMode) throws android.os.RemoteException;
public int getEqualizeMode() throws android.os.RemoteException;
public boolean setShuffleMode(int shufflemode) throws android.os.RemoteException;
public int getShuffleMode() throws android.os.RemoteException;
public boolean setRepeatMode(int repeatmode) throws android.os.RemoteException;
public int getRepeatMode() throws android.os.RemoteException;
public boolean setScanMode(int scanMode) throws android.os.RemoteException;
public int getScanMode() throws android.os.RemoteException;
public boolean informDisplayableCharacterSet(int charset) throws android.os.RemoteException;
public boolean informBatteryStatusOfCT() throws android.os.RemoteException;
public byte getPlayStatus() throws android.os.RemoteException;
public long position() throws android.os.RemoteException;
public long duration() throws android.os.RemoteException;
public long getAudioId() throws android.os.RemoteException;
public java.lang.String getTrackName() throws android.os.RemoteException;
public java.lang.String getAlbumName() throws android.os.RemoteException;
public long getAlbumId() throws android.os.RemoteException;
public java.lang.String getArtistName() throws android.os.RemoteException;
public void enqueue(long[] list, int action) throws android.os.RemoteException;
public long[] getNowPlaying() throws android.os.RemoteException;
public java.lang.String getNowPlayingItemName(long id) throws android.os.RemoteException;
public void open(long[] list, int position) throws android.os.RemoteException;
public int getQueuePosition() throws android.os.RemoteException;
public void setQueuePosition(int index) throws android.os.RemoteException;
}
