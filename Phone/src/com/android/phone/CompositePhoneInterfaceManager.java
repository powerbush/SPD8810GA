
package com.android.phone;

import java.util.List;

import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneFactory;

public class CompositePhoneInterfaceManager extends ITelephony.Stub {
    private static final String LOG_TAG = "CompositePhoneInterfaceManager";

    private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    private PhoneApp mApp;

    private PhoneInterfaceManager[] mPhoneMgr;

    CompositePhoneInterfaceManager(PhoneApp app, PhoneInterfaceManager[] phoneMgr) {
        mApp = app;
        mPhoneMgr = phoneMgr;
        publish();
    }

    private void publish() {
        if (DBG)log("publish: " + this);
        ServiceManager.addService("phone", this);
    }

    private int getSimplePolicyPhoneId() {
        return PhoneFactory.getDefaultPhoneId();
    }

    private int getOtherPhoneId() {
        if (PhoneFactory.isMultiSim()) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                if (i != getSimplePolicyPhoneId()) {
                    return i;
                }
            }
        }
        Log.d(LOG_TAG, "Is not MultiSim return DefaultPhone");
        return PhoneFactory.getDefaultPhoneId();
    }



//----------------implementation of ITelephony interface start -----------------    
    
    @Override
    public void dial(String number) throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                if (mPhoneMgr[i].isIdle()) {
                    mPhoneMgr[i].dial(number);
                    break;
                }
            }
        } else {
            mPhoneMgr[getSimplePolicyPhoneId()].dial(number);
        }
    }

    @Override
    public void call(String number) throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].call(number);
    }

    @Override
    public boolean showCallScreen() throws RemoteException {
        boolean isAble = mPhoneMgr[getSimplePolicyPhoneId()].showCallScreen();
        if (PhoneFactory.isMultiSim()) {
            if (isAble) {
                return isAble;
            } else {
                return mPhoneMgr[getOtherPhoneId()].showCallScreen();
            }
        }
        return mPhoneMgr[getSimplePolicyPhoneId()].showCallScreen();
    }

    @Override
    public boolean showCallScreenWithDialpad(boolean showDialpad) throws RemoteException {
        boolean isAble = mPhoneMgr[getSimplePolicyPhoneId()].showCallScreenWithDialpad(showDialpad);
        if (PhoneFactory.isMultiSim()) {
            if (isAble) {
                return isAble;
            } else {
                return mPhoneMgr[getOtherPhoneId()].showCallScreenWithDialpad(showDialpad);
            }
        }
        return mPhoneMgr[getSimplePolicyPhoneId()].showCallScreenWithDialpad(showDialpad);
    }

    @Override
    public boolean endCall() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            boolean endCall = false;
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                endCall = endCall || mPhoneMgr[i].endCall();
            }
            return endCall;
        } else {
            return mPhoneMgr[getSimplePolicyPhoneId()].endCall();
        }
    }

    @Override
    public void holdCall() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].holdCall();
    }

    @Override
    public void answerRingingCall() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                if (mPhoneMgr[i].isRinging()) {
                    mPhoneMgr[i].answerRingingCall();
                    break;
                }
            }
        } else {
            mPhoneMgr[getSimplePolicyPhoneId()].answerRingingCall();
        }
    }

    @Override
    public void silenceRinger() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                if (mPhoneMgr[i].isRinging()) {
                    mPhoneMgr[i].silenceRinger();
                    break;
                }
            }
        } else {
            mPhoneMgr[getSimplePolicyPhoneId()].silenceRinger();
        }
    }

    @Override
    public boolean isOffhook() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            boolean isOffhook = false;
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                isOffhook = isOffhook || mPhoneMgr[i].isOffhook();
            }
            return isOffhook;
        } else {
            return mPhoneMgr[getSimplePolicyPhoneId()].isOffhook();
        }
    }

    @Override
    public boolean isRinging() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            boolean isRinging = false;
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                isRinging = isRinging || mPhoneMgr[i].isRinging();
            }
            return isRinging;
        } else {
            return mPhoneMgr[getSimplePolicyPhoneId()].isRinging();
        }
    }

    @Override
    public boolean isIdle() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            boolean isIdle = true;
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                isIdle = isIdle && mPhoneMgr[i].isIdle();
            }
            return isIdle;
        } else {
            return mPhoneMgr[getSimplePolicyPhoneId()].isIdle();
        }
    }

    @Override
    public boolean isIccCardOn() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].isIccCardOn();
    }

    @Override
    public boolean isRadioOn() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            boolean isRadioOn = true;
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                isRadioOn = isRadioOn && mPhoneMgr[i].isRadioOn();
            }
            return isRadioOn;
        } else {
            return mPhoneMgr[getSimplePolicyPhoneId()].isRadioOn();
        }
    }

    @Override
    public boolean isSimPinEnabled() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].isSimPinEnabled();
    }

    @Override
    public void cancelMissedCallsNotification() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].cancelMissedCallsNotification();
    }

    @Override
    public boolean supplyPin(String pin) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].supplyPin(pin);
    }

    @Override
    public boolean supplyPuk(String puk, String newPin) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].supplyPuk(puk, newPin);
    }

    @Override
    public String getSmsc() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getSmsc();
    }

    @Override
    public boolean setSmsc(String smscAddr) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].setSmsc(smscAddr);
    }

    @Override
    public boolean handlePinMmi(String dialString) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].handlePinMmi(dialString);
    }

    @Override
    public boolean setIccCard(boolean turnOn) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].setIccCard(turnOn);
    }

    @Override
    public void toggleRadioOnOff() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mPhoneMgr[i].toggleRadioOnOff();
            }
        } else {
            mPhoneMgr[getSimplePolicyPhoneId()].toggleRadioOnOff();
        }
    }

    @Override
    public boolean setRadio(boolean turnOn) throws RemoteException {
        log("setRadio  turnOn" + turnOn);
        if (PhoneFactory.isMultiSim()) {
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                mPhoneMgr[i].setRadio(turnOn);
            }
        } else {
            mPhoneMgr[getSimplePolicyPhoneId()].setRadio(turnOn);
        }
        return true;
    }

    @Override
    public void updateServiceLocation() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].updateServiceLocation();
    }

    @Override
    public void enableLocationUpdates() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].disableLocationUpdates();
    }

    @Override
    public int enableApnType(String type) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].enableApnType(type);
    }

    @Override
    public int disableApnType(String type) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].disableApnType(type);
    }

    @Override
    public boolean enableDataConnectivity() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].enableDataConnectivity();
        return true;
    }

    @Override
    public boolean disableDataConnectivity() throws RemoteException {
        mPhoneMgr[getSimplePolicyPhoneId()].disableDataConnectivity();
        return true;
    }

    @Override
    public boolean isDataConnectivityPossible() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].isDataConnectivityPossible();
    }

    @Override
    public Bundle getCellLocation() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getCellLocation();
    }

    @Override
    public List<NeighboringCellInfo> getNeighboringCellInfo() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getNeighboringCellInfo();
    }

    @Override
    public int getCallState() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            if (mPhoneMgr[getSimplePolicyPhoneId()].getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                return mPhoneMgr[getOtherPhoneId()].getCallState();
            } else {
                return mPhoneMgr[getSimplePolicyPhoneId()].getCallState();
            }
        }
        return mPhoneMgr[getSimplePolicyPhoneId()].getCallState();
    }

    @Override
    public int getDataActivity() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getDataActivity();
    }

    @Override
    public int getDataState() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getDataState();
    }

    @Override
    public int getActivePhoneType() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getActivePhoneType();
    }

    @Override
    public int getCdmaEriIconIndex() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getCdmaEriIconIndex();
    }

    @Override
    public int getCdmaEriIconMode() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getCdmaEriIconMode();
    }

    @Override
    public String getCdmaEriText() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getCdmaEriText();
    }

    @Override
    public boolean getCdmaNeedsProvisioning() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getCdmaNeedsProvisioning();
    }

    @Override
    public int getVoiceMessageCount() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getVoiceMessageCount();
    }

    @Override
    public int getNetworkType() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getNetworkType();
    }

    @Override
    public boolean hasIccCard() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].hasIccCard();
    }

    @Override
    public boolean isUsimCard() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].isUsimCard();
    }

    @Override
    public boolean getIccFdnEnabled() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getIccFdnEnabled();
    }

    @Override
    public String[] Mbbms_Gsm_Authenticate(String nonce) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].Mbbms_Gsm_Authenticate(nonce);
    }

    @Override
    public String[] Mbbms_USim_Authenticate(String nonce, String autn) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].Mbbms_USim_Authenticate(nonce, autn);
    }

    @Override
    public String getSimType() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getSimType();
    }

    @Override
    public String[] getRegistrationState() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getRegistrationState();
    }

    @Override
    public boolean isVTCall() throws RemoteException {
        if (PhoneFactory.isMultiSim()) {
            boolean isVTCall = false;
            for (int i = 0; i < PhoneFactory.getPhoneCount(); i++) {
                isVTCall = isVTCall || mPhoneMgr[i].isVTCall();
            }
            return isVTCall;
        } else {
            return mPhoneMgr[getSimplePolicyPhoneId()].isVTCall();
        }
    }

    @Override
    public int getRemainTimes(int type) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getRemainTimes(type);
    }

    @Override
    public boolean setApnActivePdpFilter(String apntype, boolean filterenable)
            throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].setApnActivePdpFilter(apntype, filterenable);
    }

    @Override
    public boolean getApnActivePdpFilter(String apntype) throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getApnActivePdpFilter(apntype);
    }

    @Override
    public String[] getActiveApnTypes() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getActiveApnTypes();
    }

    @Override
    public String getActiveApn() throws RemoteException {
        return mPhoneMgr[getSimplePolicyPhoneId()].getActiveApn();
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }
}
