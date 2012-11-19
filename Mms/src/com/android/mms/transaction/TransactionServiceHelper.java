package com.android.mms.transaction;

public class TransactionServiceHelper {

    public static Class<?> getTransactionServiceClass(int phoneId) {
        switch (phoneId) {
            case 0:
            default:
                return TransactionService.class;
            case 1:
                return TransactionService1.class;
        }
    }

}
