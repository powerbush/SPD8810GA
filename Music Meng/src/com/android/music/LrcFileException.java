package com.android.music;

import java.lang.Exception;
import java.lang.RuntimeException;

// exception happens if the corresponding .lrc file cannot be found
class LrcFileNotFoundException extends Exception {
    public LrcFileNotFoundException(String msg) {
        super(msg);
    }
}

// exception happens if the textual encoding format is not supported by this feature
class LrcFileUnsupportedEncodingException extends Exception {
    public LrcFileUnsupportedEncodingException(String msg) {
        super(msg);
    }
}

// exception happens when accessing .lrc files
class LrcFileIOException extends Exception {
    public LrcFileIOException(String msg) {
        super(msg);
    }
}

// exception happern when the format of .lrc files is not correct
class LrcFileInvalidFormatException extends Exception {
    public LrcFileInvalidFormatException(String msg) {
        super(msg);
    }
}

// general exception purpose. This is a run-time exception which will cause program to exit.
// does not have to be caught.
class LrcScrollException extends RuntimeException {
    public LrcScrollException() {
        super();
    }
}
