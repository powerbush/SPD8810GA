
package com.android.mms.transaction;

public class TransactionDataconnectionState {

    public static final int TRANSACTION_STATE_PROCESSING = 11;

    public static final int TRANSACTION_STATE_PENDING = 12;

    public static final int TRANSACTION_STATE_IDLE = 10;

    private static final int TRANSACTION_STARTING_TIME_OUT = 2*60*1000;

    private int[] transcationState = new int[] { TRANSACTION_STATE_IDLE, TRANSACTION_STATE_IDLE };

    private long[] transcationStartTime = new long[] { 0, 0 };

    private static TransactionDataconnectionState transactionDataconnectionState = new TransactionDataconnectionState();

    private TransactionDataconnectionState() {
        super();
    }

    public static boolean isDataconnectionStartingTimeout(int phoneId) {
       return (System.currentTimeMillis() - transactionDataconnectionState.transcationStartTime[phoneId]) > TRANSACTION_STARTING_TIME_OUT ;
    }

    synchronized public static int getDataconnectionState(int phoneId) {
        int stateIndex = transactionDataconnectionState.getStateIndex(phoneId);
        if (transactionDataconnectionState.transcationState[stateIndex] == TRANSACTION_STATE_PROCESSING) {
            transactionDataconnectionState.transcationState[phoneId] = TRANSACTION_STATE_PENDING;
            return TRANSACTION_STATE_PENDING;
        } else {
            transactionDataconnectionState.transcationState[phoneId] = TRANSACTION_STATE_PROCESSING;
            if(transactionDataconnectionState.transcationStartTime[phoneId] == 0)
                transactionDataconnectionState.transcationStartTime[phoneId] = System.currentTimeMillis();
            return TRANSACTION_STATE_PROCESSING;
        }
    }

    synchronized public static void setEndDataconnectionState(int phoneId) {
        transactionDataconnectionState.transcationState[phoneId] = TransactionDataconnectionState.TRANSACTION_STATE_IDLE;
        transactionDataconnectionState.transcationStartTime[phoneId] = 0;
    }

    private int getStateIndex(int phoneId) {
        if (phoneId == 0) {
            return 1;
        } else {
            return 0;
        }
    }
}
