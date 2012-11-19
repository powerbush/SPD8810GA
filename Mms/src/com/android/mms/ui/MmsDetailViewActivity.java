package com.android.mms.ui;


import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.drm.mobile1.DrmException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.provider.Browser;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.Telephony.Mms;
import android.text.ClipboardManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.GifDecodeInterface;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;
import android.text.util.Linkify;

import com.android.mms.R;
import com.android.mms.model.AudioModel;
import com.android.mms.model.FileModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.TextModel;
import com.android.mms.model.VcardModel;
import com.android.mms.model.VideoModel;
import com.android.mms.util.ZoomViewUtil;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendReq;
import android.os.AsyncTask;
import java.io.FileNotFoundException;

/**
 * fixed CR<NEWMS00110179> by luning at 11-08-12
 * @author luning
 *
 */
public class MmsDetailViewActivity extends Activity
            implements ZoomViewUtil.TextResizeable {

	private static final String TAG = "MmsDetailViewActivity";
	private LinearLayout main;
	private LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
			LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
	private Uri mMessageUri;
	private SlideshowModel mSlideShowModel;
	private String mSubject;
	private boolean mLocked;
	private ArrayList<XImageView> mGifViews;
	private ArrayList<ImageModel> mGifImageModels;

	private static final int MENU_VIEW_SLIDESHOW = 0;
	private static final int MENU_FORWARD_MESSAGE = 1;
	private static final int MENU_DELETE_MESSAGE = 2;
	private static final int MENU_COPY_TEXT = 3;
	private TextView mBodyTextView;
	private float mDefatltTextSize = 16.0f;
	private ZoomViewUtil mZoom;

	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_VIEW_SLIDESHOW, 0, R.string.play)
				.setIcon(R.drawable.ic_menu_play_clip);
		menu.add(Menu.NONE, MENU_FORWARD_MESSAGE, 0, R.string.menu_forward)
				.setIcon(android.R.drawable.ic_menu_send);
		menu.add(Menu.NONE, MENU_DELETE_MESSAGE, 0, R.string.delete_message)
				.setIcon(android.R.drawable.ic_menu_delete);
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_VIEW_SLIDESHOW:
			MessageUtils.viewMmsMessageAttachment(this, mMessageUri,
					mSlideShowModel);
			break;
		case MENU_FORWARD_MESSAGE:
			forwardMessage();
			break;
		case MENU_DELETE_MESSAGE:
			DeleteMessageListener l = new DeleteMessageListener(mMessageUri,
					mLocked);
			confirmDeleteDialog(l, mLocked);
			break;
		}
		return true;
	}

	private void forwardMessage() {
		Intent intent = new Intent(this, ComposeMessageActivity.class);

		intent.putExtra("exit_on_sent", true);
		intent.putExtra("forwarded_message", true);

		SendReq sendReq = new SendReq();
		String subject = getString(R.string.forward_prefix);
		if (mSubject != null) {
			subject += mSubject;
		}
		sendReq.setSubject(new EncodedStringValue(subject));
		sendReq.setBody(mSlideShowModel.makeCopy(this));

		Uri uri = null;
		try {
			PduPersister persister = PduPersister.getPduPersister(this);
			// Copy the parts of the message here.
			uri = persister.persist(sendReq, Mms.Draft.CONTENT_URI);
		} catch (MmsException e) {
			Log.e(TAG, "Failed to copy message: " + mMessageUri, e);
			Toast.makeText(this, R.string.cannot_save_message,
					Toast.LENGTH_SHORT).show();
			return;
		}

		intent.putExtra("msg_uri", uri);
		intent.putExtra("subject", subject);

		// ForwardMessageActivity is simply an alias in the manifest for
		// ComposeMessageActivity.
		// We have to make an alias because ComposeMessageActivity launch flags
		// specify
		// singleTop. When we forward a message, we want to start a separate
		// ComposeMessageActivity.
		// The only way to do that is to override the singleTop flag, which is
		// impossible to do
		// in code. By creating an alias to the activity, without the singleTop
		// flag, we can
		// launch a separate ComposeMessageActivity to edit the forward message.
		intent.setClassName(this, "com.android.mms.ui.ForwardMessageActivity");
        try{
            startActivity(intent);
        }catch(ActivityNotFoundException ex){
            Log.d(TAG, "forwardMessage ActivityNotFoundException :"+ex.toString());
            Toast.makeText(this, R.string.activity_not_found ,Toast.LENGTH_LONG).show();
        }
	}

	private void confirmDeleteDialog(DialogInterface.OnClickListener listener,
			boolean locked) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(locked ? R.string.confirm_dialog_locked_title
				: R.string.confirm_dialog_title);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setCancelable(true);
		builder.setMessage(locked ? R.string.confirm_delete_locked_message
				: R.string.confirm_delete_message);
		builder.setPositiveButton(R.string.delete, listener);
		builder.setNegativeButton(R.string.no, null);
		builder.show();
	}

	private class DeleteMessageListener implements
			DialogInterface.OnClickListener {
		private final Uri mDeleteUri;
		private final boolean mDeleteLocked;

		public DeleteMessageListener(Uri uri, boolean deleteLocked) {
			mDeleteUri = uri;
			mDeleteLocked = deleteLocked;
		}

		public void onClick(DialogInterface dialog, int whichButton) {
			AsyncQueryHandler asyncQuerHandler = new AsyncQueryHandler(
					getContentResolver()) {
			};
			asyncQuerHandler.startDelete(9700, null, mDeleteUri,
					mDeleteLocked ? null : "locked=0", null);
			finish();
		}
	}

	protected void onPause() {
		super.onPause();
		for (XImageView xImageView : mGifViews) {
			if (null != xImageView) {
				xImageView.recycleGif();
				xImageView = null;
			}
		}
	}

	protected void onResume() {
		super.onResume();
		int size = mGifViews.size();
		XImageView xImageView = null;
		ImageModel image = null;
        for (int i = 0; i < size; i++) {
            xImageView = mGifViews.get(i);
            image = mGifImageModels.get(i);
            xImageView.parseGifImage(image.getUri(), image.getBitmap());/*modify by luning at 2011.12.20 */
        }
	}

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mGifViews = new ArrayList<XImageView>();
        mGifImageModels = new ArrayList<ImageModel>();
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setGravity(Gravity.CENTER);
        main.setPadding(20, 20, 20, 20);

        mZoom = new ZoomViewUtil(this);
        main.setLongClickable(true);

        layoutParams.topMargin = 10;
        Intent intent = getIntent();
        mMessageUri = intent.getData();
        String details = intent.getStringExtra("msg_details");
        mSubject = intent.getStringExtra("msg_subject");
        mLocked = intent.getBooleanExtra("msg_locked", false);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String textSize = sp.getString(MessagingPreferenceActivity.SET_SMS_TEXT_SIZE, null);
        mDefatltTextSize = Float.parseFloat(textSize);
        addText(details);
        try {
            mSlideShowModel = SlideshowModel.createFromMessageUri(this, mMessageUri, true);
        } catch (MmsException e) {
            Log.e(TAG, "Cannot create SlideshowModel", e);
            finish();
            return;
        }
        getView(mSlideShowModel);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(main, layoutParams);
        this.setContentView(scrollView);

        mZoom.setView(main, mDefatltTextSize);
    }

    public void onTextResize(float size) {
        for ( int i = 0; i < main.getChildCount(); i++ ) {
            View view = main.getChildAt(i);
            if ( view instanceof TextView ) {
                ((TextView)view).setTextSize(size);
            }
        }
    }

    private void copyToClipboard(String str) {
        ClipboardManager clip =
            (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setText(str);
    }

	private void getView(SlideshowModel model) {
		for (SlideModel smodel : model) {
			getDatafromModel(smodel);
		}
	}

    private void getDatafromModel(SlideModel model) {
        try {
            MediaModel[] medias = new MediaModel[2];
            for (MediaModel media : model) {
                if (media.isText()) {
                    if (((TextModel) media).getRegion().getTop() == 0) {
                        medias[0] = media;
                    } else {
                        medias[1] = media;
                    }
                } else if (media.isAudio()) {
                    if (medias[0] != null) {
                        medias[1] = media;
                    } else {
                        medias[0] = media;
                    }
                } else if (media.isImage()) {
                    if (medias[0] == null && ((ImageModel) media).getRegion().getTop() == 0) {
                        medias[0] = media;
                    } else {
                        medias[1] = media;
                    }
                } else if(media.isVcard()) {
                    medias[0] = media;
                } else if(media.isOtherFile()) {
                    medias[0] = media;
                }
                else if (((VideoModel) media).getRegion().getTop() == 0) {
                    if (medias[0] != null) {
                        medias[1] = media;
                    } else {
                        medias[0] = media;
                    }
                }
            }

            for (MediaModel media : medias) {
                if (media == null) {
                    continue;
                }
                if (media.isText()) {
                    getText((TextModel) media);
                } else if (media.isAudio()) {
                    getAudio((AudioModel) media);
                } else if (media.isImage()) {
                    getImage((ImageModel) media);
                } else if (media.isVideo()) {
                    getVideo((VideoModel) media);
                } else if (media.isVcard()) {
                    addVcardView((VcardModel)media);
                } else if (media.isOtherFile()) {
                    addFileView((FileModel)media);
                }
            }

        } catch (DrmException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

	/**
	 * get audio data and add to layout
	 *
	 * @param audio
	 * @throws DrmException
	 */
	private void getAudio(final AudioModel audio) throws DrmException {
		if (null == audio) {
			return;
		}
		LinearLayout audioView = new LinearLayout(this);
		audioView.setOrientation(LinearLayout.HORIZONTAL);
		audioView.setGravity(Gravity.CENTER_VERTICAL);
		ImageView audioIcon = new ImageView(this);
		audioIcon.setBackgroundResource(R.drawable.ic_launcher_musicplayer_2);
		TextView audioName = new TextView(this);
		audioName.setText(audio.getSrc());
		audioView.addView(audioIcon);
		audioView.addView(audioName);
		main.addView(audioView, layoutParams);
		audioView.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showDialog(audio);
			}
		});
	}

	/**
	 * add a textview to layout
	 *
	 * @param text
	 */
	private void addText(String text) {
		if (text == null) {
			return;
		}
		TextView textView = new TextView(this);
		textView.setTextSize(mDefatltTextSize);
		textView.setAutoLinkMask(Linkify.ALL);
		//textView.getAutoLinkMask();
		textView.setText(text);
		//textView.setClickable(true);
		textView.setOnCreateContextMenuListener(new TextViewCreateContextMenu());
		//add for to show dialog about send mms,tel someone,browse website. --start
        CharSequence te = textView.getText();
        if (te instanceof Spannable) {
            Spannable sp = (Spannable) textView.getText();
            URLSpan[] urls = sp.getSpans(0, te.length(), URLSpan.class);
            SpannableStringBuilder style = new SpannableStringBuilder(te);
            style.clearSpans();
            for (URLSpan url : urls) {
                MyURLSpan mySpan = new MyURLSpan(url.getURL());
                style.setSpan(mySpan, sp.getSpanStart(url), sp.getSpanEnd(url),
                        Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
            }
            textView.setText(style);
        }
      //add for to show dialog about send mms,tel someone,browse website. --end
		//new ZoomViewUtil(textView);
		main.addView(textView, layoutParams);
		mZoom.setView(textView, mDefatltTextSize);
	}

	   /**
     * add a vardview to layout
     *
     * @param text
     */
    private void addVcardView(final VcardModel vcard) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        TextView vcardName = new TextView(this);
        vcardName.setTextSize(16);
        vcardName.setClickable(false);

        ImageView vcardIcon = new ImageView(this);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER);
        ll.addView(vcardIcon, params);
        ll.addView(vcardName, params);
        ll.setPadding(0, 10, 0, 0);

        vcardIcon.setImageResource(R.drawable.vcf);
        vcardName.setText(R.string.attach_vcard);

        main.addView(ll, layoutParams);

        final Context context = this;
        ll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(context)
                        .setMessage(
                                context.getString(R.string.menu_add_address_to_contacts,
                                        vcard.getSrc()))
                        .setIcon(R.drawable.vcf)
                        .setTitle(R.string.menu_add_to_contacts)
                        .setPositiveButton(context.getString(R.string.yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setData(vcard.getUri());
                                        intent.setClassName("com.android.contacts",
                                                "com.android.contacts.ImportVCardActivity");
                                        context.startActivity(intent);
                                    }
                                }).setNegativeButton(context.getString(R.string.no), null).show();
            }
        });
    }

    /**
     * add a vardview to layout
     * 
     * @param text
     */
    private void addFileView(final FileModel file) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        TextView fileName = new TextView(this);
        fileName.setTextSize(16);
        fileName.setClickable(false);

        ImageView fileIcon = new ImageView(this);

        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER);
        ll.addView(fileIcon, params);
        ll.addView(fileName, params);
        ll.setPadding(0, 10, 0, 0);
        fileName.setText(file.getSrc());


        final int iconId = MessageUtils.getFileIconId(file.getSrc());
        fileIcon.setImageResource(iconId);

        main.addView(ll, layoutParams);

        final Context context = this;
        ll.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(context)
                        .setItems(new String[] {
                            context.getString(R.string.copy_to_sdcard)
                        }, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (MessageUtils.showSaveErroDialog(context, file.getSrc(),
                                        file.getMediaSize())) {
                                    if (!MessageUtils.saveFile(context, file,
                                            MessageUtils.SAVE_MMS_DIR)) {
                                        Toast.makeText(
                                                context,
                                                context.getString(R.string.save_file_error_msg,
                                                        file.getSrc()), Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(
                                                context,
                                                context.getString(R.string.save_file_ok_msg,
                                                        file.getSrc()), Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        }).setIcon(iconId).setTitle(file.getSrc())
                        .setNegativeButton(R.string.no, null)
                        .show();
            }
        });
    }

	private class TextViewCreateContextMenu implements OnCreateContextMenuListener{

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
                mBodyTextView = (TextView)v;
               menu.setHeaderTitle(R.string.message_options);
               menu.add(0,MENU_COPY_TEXT , 0, R.string.copy_message_text).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                   @Override
                public boolean onMenuItemClick(MenuItem item) {
                    copyToClipboard(mBodyTextView.getText().toString());
                    return true;
                }
            });
        }

	}
	/**
	 * get text data and add to layout
	 *
	 * @param text
	 */
	private void getText(TextModel text) {
		String textInfo = text.getText();
		addText(textInfo);
	}

	/**
	 * get image data and add to layout
	 *
	 * @param image
	 * @throws DrmException
	 */
	private void getImage(final ImageModel image) throws DrmException {
		if (image == null) {
			return;
		}
		XImageView imageView = new XImageView(this);
		// gif image
		if (image.getContentType().equals(ContentType.IMAGE_GIF)) {
			// prepare to free
			mGifViews.add(imageView);
			mGifImageModels.add(image);
		}
		// bitmap image
		else {
			Bitmap bm = image.getBitmapWithDrmCheck();
            if (bm != null) {
                int width = bm.getWidth();
                if (width > 200) {
                    bm = zoomBitmap(bm, 200);
                }
            }
			imageView.setImageBitmap(bm);
		}
		main.addView(imageView, layoutParams);
		imageView.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showDialog(image);
			}
		});
	}

	public static Bitmap createVideoThumbnail(Context context, Uri uri) {
		Bitmap bitmap = null;
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			//TS for compile
			//retriever.setMode(MediaMetadataRetriever.MODE_CAPTURE_FRAME_ONLY);
			retriever.setDataSource(context, uri);
			//TS for compile
			bitmap = retriever.getFrameAtTime();
		} catch (RuntimeException ex) {
			// Assume this is a corrupt video file.
		} finally {
			try {
				retriever.release();
			} catch (RuntimeException ex) {
				// Ignore failures while cleaning up.
			}
		}
		return bitmap;
	}

	/**
	 * get video data and add to layout
	 *
	 * @param video
	 * @throws DrmException
	 */
	private void getVideo(final VideoModel video) throws DrmException {
		Bitmap bm = createVideoThumbnail(this, video.getUri());
		if (null == bm) {
			bm = BitmapFactory.decodeResource(getResources(),
					R.drawable.ic_missing_thumbnail_video);
		} else {
			bm = zoomBitmap(bm, 200);
			Canvas canvas = new Canvas(bm);
			Bitmap icon = BitmapFactory.decodeResource(getResources(),
					R.drawable.ic_gallery_video_overlay);
			int x = (bm.getWidth() - icon.getWidth()) >> 1;
			int y = (bm.getHeight() - icon.getHeight()) >> 1;
			canvas.drawBitmap(icon, x, y, new Paint());
		}
		ImageView imageView = new ImageView(this);
		imageView.setImageBitmap(bm);
		main.addView(imageView, layoutParams);
		imageView.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showDialog(video);
			}
		});
	}

	/**
	 * open a media item
	 *
	 * @param mm
	 *            MediaModel instance
	 */
	private void openMedia(MediaModel mm) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		String contentType;
		if (mm.isDrmProtected()) {
			contentType = mm.getDrmObject().getContentType();
		} else {
			contentType = mm.getContentType();
			contentType = contentType.toLowerCase();
		}
		intent.setDataAndType(mm.getUri(), contentType);
		try{
		    startActivity(intent);
		}catch(ActivityNotFoundException ex){
		    Log.d(TAG, "openMedia ActivityNotFoundException :"+ex.toString());
		    Toast.makeText(this, R.string.filetype_not_support ,Toast.LENGTH_LONG).show();
		}

	}

	/**
	 * when touch a media item,show dialog
	 *
	 * @param mm
	 *            MediaModel instance
	 */
    private void showDialog(final MediaModel mm) {
        DialogInterface.OnClickListener itemClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // open medaia
                if (which == 0) {
                    openMedia(mm);
                    // save to sdcard
                } else if (which == 1) {
                    // lino add begin
                    if (MessageUtils.showSaveErroDialog(MmsDetailViewActivity.this, mm.getSrc(), mm
                            .getMediaSize())) {
                        if (!MessageUtils.saveFile(MmsDetailViewActivity.this, mm,
                              Environment.DIRECTORY_DOWNLOADS)) {
                            Toast.makeText(MmsDetailViewActivity.this,
                                    getString(R.string.save_file_error_msg, mm.getSrc()),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            String dir = Environment.getExternalStorageDirectory() + "/"
                                    + Environment.DIRECTORY_DOWNLOADS + "/";
//                            String dir = Environment.getExternalStorageDirectory() + "/"
//                                    + MessageUtils.SAVE_MMS_DIR+ "/";//fix for bug 11788
                            dir = getString(R.string.copy_to_sdcard_success, dir);
                            Toast.makeText(MmsDetailViewActivity.this, dir, Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                }
                // lino add end
            }
        };
        new AlertDialog.Builder(this).setTitle(R.string.select_link_title).setItems(
                new String[] {
                        getResources().getString(R.string.view),
                        getResources().getString(R.string.copy_to_sdcard)
                }, itemClickListener).setNegativeButton(R.string.no, null).show();
    }

	private static Bitmap zoomBitmap(Bitmap bitmap, int w) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		Matrix matrix = new Matrix();
		float scaleWidth = ((float) w / width);
		float scaleHeight = scaleWidth;
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap newbm = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix,
				true);
		return newbm;
	}

	private class XImageView extends ImageView implements GifDecodeInterface {

		public Handler handler;

		public Runnable handleGif;

		private GifHandler gifHandler;

		// init gif data return first frame
		public Bitmap initGif(InputStream data) throws OutOfMemoryError{

			if (null == gifHandler) {
				gifHandler = new GifHandler();
			}
			if (null == handler) {
				handler = new Handler();
			}
			if (null == handleGif) {
				handleGif = new Runnable() {
					@Override
					public void run() {
						nextFrame();
					}
				};
			}
			return gifHandler.initGifData(data);
		}

		// set next frame from gif data
		public void nextFrame() {
			if (handler != null && gifHandler != null) {
				setImageBitmap(gifHandler.nextFrameBitmap());
				handler.postDelayed(handleGif, gifHandler.getDelay());
			}
		}

		public void recycleGif() {

			if (null != handler) {
				handler.removeCallbacks(handleGif);
				handler = null;
				handleGif = null;
			}
			if (null != gifHandler) {
				gifHandler.recycleDecode();
				gifHandler = null;
			}
			cancelTask();
		}

		public XImageView(Context context) {
			super(context);
		}

        public void setGifImage(Uri uri, Bitmap bitmap) {
            if (null != uri) {
                /*modify by luning at 2011.12.20 begin*/
//                try {
//                    InputStream data = mContext.getContentResolver().openInputStream(uri);
//                    initGif(data);
//                    nextFrame();
//                } catch (OutOfMemoryError error) {
//                    recycleGif();
//                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
//                            R.drawable.ic_missing_thumbnail_picture);
//                    setImageBitmap(bitmap);
//                } catch (Exception exception) {
//                    recycleGif();
//                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
//                            R.drawable.ic_missing_thumbnail_picture);
//                    setImageBitmap(bitmap);
//                }

                try {
                    InputStream data = mContext.getContentResolver().openInputStream(uri);
                    if (null != initGif(data)) {
                        nextFrame();
                    } else {
                        showDefaultImage(bitmap);
                    }
                } catch (OutOfMemoryError error) {
                    showDefaultImage(bitmap);
                } catch (Exception exception) {
                    showDefaultImage(bitmap);
                }
                /*modify by luning at 2011.12.20 end*/
            }
        }

		/*modify by luning at 2011.12.20 begin*/
        private void showDefaultImage(Bitmap bitmap) {
            recycleGif();
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_picture);
            }
            setImageBitmap(bitmap);
        }
        /*modify by luning at 2011.12.20 end*/
        private ParseImageTask parseTask = null;
        private class ParseImageTask extends AsyncTask<InputStream,Integer,Integer> {
            Bitmap b = null;
            public ParseImageTask(Bitmap b) {
                this.b = b;
            }
            @Override
            protected Integer doInBackground(InputStream... in) {
                try {
                    Bitmap bmp = null;

                    if(isCancelled()) {
                        return -1;
                    }
                    bmp = gifHandler.initGifData(in[0]);
                    if(null != bmp) {
                        return 0;
                    } else {
                        return 1;
                    }
                } catch (OutOfMemoryError error) {
                    return 1;
                } catch (Exception exception) {
                    return 1;
                }
            }
            @Override
            protected void onPostExecute(Integer i) {
                if(isCancelled()) {
                    return;
                }
                if (i.intValue() == 0) {
                    nextFrame();
                } else if(i.intValue() == 1){
                    showDefaultImage(b);
                }
            }
        }
        public void parseGifImage(Uri uri, Bitmap bitmap) {
            if (null != uri) {
                try{
                    if (null == gifHandler) {
                        gifHandler = new GifHandler();
                    }
                    if (null == handler) {
                        handler = new Handler();
                    }
                    if (null == handleGif) {
                        handleGif = new Runnable() {
                            @Override
                            public void run() {
                                nextFrame();
                            }
                        };
                    }
                    cancelTask();
                    parseTask = new ParseImageTask(bitmap);
                    if(null != parseTask) {
                        parseTask.execute(mContext.getContentResolver().openInputStream(uri));
                    }
                }catch(FileNotFoundException e){
                }
                /*modify by luning at 2011.12.20 end*/
            }
        }
        public void cancelTask() {
            if(null != parseTask) {
                parseTask.cancel(true);
                parseTask = null;
            }
        }
	}
    private class MyURLSpan extends ClickableSpan {
        private String mUrl;

        public MyURLSpan(String mUrl) {
            super();
            this.mUrl = mUrl;
        }

        @Override
        public void onClick(View widget) {
            final java.util.ArrayList<String> newUrls = new java.util.ArrayList<String>();

            if (mUrl.indexOf("tel:") >= 0) {
                newUrls.add(mUrl);
                newUrls.add("smsto:" + mUrl.substring(4));
            } else if (mUrl.indexOf("http://") >= 0) {
                newUrls.clear();
                newUrls.add(mUrl);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(MmsDetailViewActivity.this,
                    android.R.layout.select_dialog_item, newUrls) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    try {
                        String url = getItem(position).toString();
                        TextView tv = (TextView) v;
                        Drawable d = MmsDetailViewActivity.this.getPackageManager()
                                .getActivityIcon(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        if (d != null) {
                            d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                            tv.setCompoundDrawablePadding(10);
                            tv.setCompoundDrawables(d, null, null, null);
                        }
                        final String telPrefix = "tel:";
                        final String smsPrefix = "smsto:";
                        final String browserPrefix = "http://";
                        if (url.startsWith(telPrefix)) {
                            url = url.substring(telPrefix.length());
                        } else if (url.startsWith(smsPrefix)) {
                            url = url.substring(smsPrefix.length());
                        } else if (url.startsWith(browserPrefix)) {
                            url = url.substring(browserPrefix.length());
                        }
                        tv.setText(url);
                    } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                    }
                    return v;
                }
            };
            AlertDialog.Builder b = new AlertDialog.Builder(MmsDetailViewActivity.this);
            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    if (which >= 0) {
                        Uri uri = Uri.parse(newUrls.get(which));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID,
                                MmsDetailViewActivity.this.getPackageName());
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        MmsDetailViewActivity.this.startActivity(intent);
                    }
                    dialog.dismiss();
                }
            };
            b.setTitle(R.string.select_link_title);
            b.setCancelable(true);
            b.setAdapter(adapter, click);
            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            b.show();
        }
    }
}
