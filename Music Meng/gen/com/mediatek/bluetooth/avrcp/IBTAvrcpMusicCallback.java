/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: D:\\cvs\\alps13low0907\\alps\\packages\\apps\\Music\\src\\com\\mediatek\\bluetooth\\avrcp\\IBTAvrcpMusicCallback.aidl
 */
package com.mediatek.bluetooth.avrcp;
public interface IBTAvrcpMusicCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback
{
private static final java.lang.String DESCRIPTOR = "com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback interface,
 * generating a proxy if needed.
 */
public static com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback))) {
return ((com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback)iin);
}
return new com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback.Stub.Proxy(obj);
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
case TRANSACTION_notifyPlaybackStatus:
{
data.enforceInterface(DESCRIPTOR);
byte _arg0;
_arg0 = data.readByte();
this.notifyPlaybackStatus(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_notifyTrackChanged:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
this.notifyTrackChanged(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_notifyTrackReachStart:
{
data.enforceInterface(DESCRIPTOR);
this.notifyTrackReachStart();
reply.writeNoException();
return true;
}
case TRANSACTION_notifyTrackReachEnd:
{
data.enforceInterface(DESCRIPTOR);
this.notifyTrackReachEnd();
reply.writeNoException();
return true;
}
case TRANSACTION_notifyPlaybackPosChanged:
{
data.enforceInterface(DESCRIPTOR);
this.notifyPlaybackPosChanged();
reply.writeNoException();
return true;
}
case TRANSACTION_notifyAppSettingChanged:
{
data.enforceInterface(DESCRIPTOR);
this.notifyAppSettingChanged();
reply.writeNoException();
return true;
}
case TRANSACTION_notifyNowPlayingContentChanged:
{
data.enforceInterface(DESCRIPTOR);
this.notifyNowPlayingContentChanged();
reply.writeNoException();
return true;
}
case TRANSACTION_notifyVolumehanged:
{
data.enforceInterface(DESCRIPTOR);
byte _arg0;
_arg0 = data.readByte();
this.notifyVolumehanged(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.mediatek.bluetooth.avrcp.IBTAvrcpMusicCallback
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
public void notifyPlaybackStatus(byte status) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeByte(status);
mRemote.transact(Stub.TRANSACTION_notifyPlaybackStatus, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyTrackChanged(long id) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(id);
mRemote.transact(Stub.TRANSACTION_notifyTrackChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyTrackReachStart() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_notifyTrackReachStart, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyTrackReachEnd() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_notifyTrackReachEnd, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyPlaybackPosChanged() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_notifyPlaybackPosChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyAppSettingChanged() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_notifyAppSettingChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyNowPlayingContentChanged() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_notifyNowPlayingContentChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void notifyVolumehanged(byte volume) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeByte(volume);
mRemote.transact(Stub.TRANSACTION_notifyVolumehanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_notifyPlaybackStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_notifyTrackChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_notifyTrackReachStart = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_notifyTrackReachEnd = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_notifyPlaybackPosChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_notifyAppSettingChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_notifyNowPlayingContentChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_notifyVolumehanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
}
public void notifyPlaybackStatus(byte status) throws android.os.RemoteException;
public void notifyTrackChanged(long id) throws android.os.RemoteException;
public void notifyTrackReachStart() throws android.os.RemoteException;
public void notifyTrackReachEnd() throws android.os.RemoteException;
public void notifyPlaybackPosChanged() throws android.os.RemoteException;
public void notifyAppSettingChanged() throws android.os.RemoteException;
public void notifyNowPlayingContentChanged() throws android.os.RemoteException;
public void notifyVolumehanged(byte volume) throws android.os.RemoteException;
}
