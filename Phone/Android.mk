
# copy vt subtitute profile picture to prebuild directory
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

#+++++++++++++++
file := $(TARGET_OUT)/opl/etc/vt_substitute.jpg
ALL_PREBUILT += $(file)
$(file) : $(LOCAL_PATH)/vt_substitute.jpg | $(ACP)
	$(transform-prebuilt-to-target)
#+++++++++++++++

include $(CLEAR_VARS)

# Static library with some common classes for the phone apps.
# To use it add this line in your Android.mk
#  LOCAL_STATIC_JAVA_LIBRARIES := com.android.phone.common
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user

LOCAL_SRC_FILES := \
	src/com/android/phone/ButtonGridLayout.java \
	src/com/android/phone/CallLogAsync.java \
	src/com/android/phone/HapticFeedback.java

LOCAL_MODULE := com.android.phone.common
include $(BUILD_STATIC_JAVA_LIBRARY)

# Build the Phone app which includes the emergency dialer. See Contacts
# for the 'other' dialer.
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += \
        src/com/android/phone/EventLogTags.logtags \
        src/com/android/phone/INetworkQueryService.aidl \
        src/com/android/phone/INetworkQueryServiceCallback.aidl

LOCAL_PACKAGE_NAME := Phone
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

# Build the test package
include $(call all-makefiles-under,$(LOCAL_PATH))
