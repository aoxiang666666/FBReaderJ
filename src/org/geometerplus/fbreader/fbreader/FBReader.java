/*
 * Copyright (C) 2007-2009 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.fbreader;

import java.io.*;
import java.util.*;
import org.geometerplus.zlibrary.core.util.*;

import org.geometerplus.zlibrary.core.filesystem.*;
import org.geometerplus.zlibrary.core.application.*;
import org.geometerplus.zlibrary.core.dialogs.ZLDialogManager;
import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.core.options.*;
import org.geometerplus.zlibrary.core.view.ZLPaintContext;
import org.geometerplus.zlibrary.core.view.ZLViewWidget;

import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.view.ZLTextView;
import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;

import org.geometerplus.fbreader.bookmodel.BookModel;
import org.geometerplus.fbreader.collection.BookCollection;
import org.geometerplus.fbreader.collection.RecentBooks;
import org.geometerplus.fbreader.collection.BookDescription;

public final class FBReader extends ZLApplication {
	static interface ViewMode {
		int UNDEFINED = 0;
		int BOOK_TEXT = 1 << 0;
		int FOOTNOTE = 1 << 1;
	};

	public final ZLBooleanOption UseSeparateBindingsOption = 
		new ZLBooleanOption("KeysOptions", "UseSeparateBindings", false);

	public final ScrollingOptions TrackballScrollingOptions =
		new ScrollingOptions("TrackballScrolling", ZLTextView.ScrollingMode.SCROLL_LINES);
	
	private final ZLStringOption myBookNameOption =
		new ZLStringOption("State", "Book", "");

	private final ZLKeyBindings myBindings0 = new ZLKeyBindings("Keys");
	private final ZLKeyBindings myBindings90 = new ZLKeyBindings("Keys90");
	private final ZLKeyBindings myBindings180 = new ZLKeyBindings("Keys180");
	private final ZLKeyBindings myBindings270 = new ZLKeyBindings("Keys270");

	private int myMode = ViewMode.UNDEFINED;
	private int myPreviousMode = ViewMode.BOOK_TEXT;

	public final BookTextView BookTextView;
	public final FootnoteView FootnoteView;

	public BookModel Model;

	private final String myArg0;

	public FBReader(String[] args) {
		myArg0 = (args.length > 0) ? args[0] : null;
		addAction(ActionCode.QUIT, new QuitAction(this));
		addAction(ActionCode.ROTATE_SCREEN, new ZLApplication.RotationAction());

		addAction(ActionCode.UNDO, new UndoAction(this));
		addAction(ActionCode.REDO, new RedoAction(this));

		addAction(ActionCode.INCREASE_FONT, new ChangeFontSizeAction(this, +2));
		addAction(ActionCode.DECREASE_FONT, new ChangeFontSizeAction(this, -2));

		addAction(ActionCode.SHOW_LIBRARY, new ShowLibrary(this));
		addAction(ActionCode.SHOW_OPTIONS, new ShowOptionsDialogAction(this));
		addAction(ActionCode.SHOW_PREFERENCES, new PreferencesAction(this));
		addAction(ActionCode.SHOW_BOOK_INFO, new BookInfoAction(this));
		addAction(ActionCode.SHOW_CONTENTS, new ShowTOCAction(this));
		
		addAction(ActionCode.SEARCH, new SearchAction(this));
		addAction(ActionCode.FIND_NEXT, new FindNextAction(this));
		addAction(ActionCode.FIND_PREVIOUS, new FindPreviousAction(this));
		
		addAction(ActionCode.SCROLL_TO_HOME, new ScrollToHomeAction(this));
		addAction(ActionCode.SCROLL_TO_START_OF_TEXT, new DummyAction(this));
		addAction(ActionCode.SCROLL_TO_END_OF_TEXT, new DummyAction(this));
		addAction(ActionCode.VOLUME_KEY_SCROLL_FORWARD, new VolumeKeyScrollingAction(this, true));
		addAction(ActionCode.VOLUME_KEY_SCROLL_BACKWARD, new VolumeKeyScrollingAction(this, false));
		addAction(ActionCode.TRACKBALL_SCROLL_FORWARD, new ScrollingAction(this, TrackballScrollingOptions, true));
		addAction(ActionCode.TRACKBALL_SCROLL_BACKWARD, new ScrollingAction(this, TrackballScrollingOptions, false));
		addAction(ActionCode.CANCEL, new CancelAction(this));
		addAction(ActionCode.GOTO_NEXT_TOC_SECTION, new DummyAction(this));
		addAction(ActionCode.GOTO_PREVIOUS_TOC_SECTION, new DummyAction(this));
		//addAction(ActionCode.COPY_SELECTED_TEXT_TO_CLIPBOARD, new DummyAction(this));
		//addAction(ActionCode.OPEN_SELECTED_TEXT_IN_DICTIONARY, new DummyAction(this));
		//addAction(ActionCode.CLEAR_SELECTION, new DummyAction(this));

		ZLPaintContext context = ZLibrary.Instance().getPaintContext();
		BookTextView = new BookTextView(context);
		FootnoteView = new FootnoteView(context);

		setMode(ViewMode.BOOK_TEXT);
	}
		
	public void initWindow() {
		super.initWindow();
		refreshWindow();
		String fileName = null;
		if (myArg0 != null) {
			try {
				fileName = new File(myArg0).getCanonicalPath();
			} catch (IOException e) {
			}
		}
		if (!openFile(fileName) && !openFile(myBookNameOption.getValue())) {
			openFile(BookCollection.Instance().getHelpFileName());
		}
	}
	
	public void openBook(BookDescription bookDescription) {
		OpenBookRunnable runnable = new OpenBookRunnable(bookDescription);
		ZLDialogManager.getInstance().wait("loadingBook", runnable);
	}

	public ZLKeyBindings keyBindings() {
		return UseSeparateBindingsOption.getValue() ?
				keyBindings(myViewWidget.getRotation()) : myBindings0;
	}
	
	public ZLKeyBindings keyBindings(int angle) {
		switch (angle) {
			case ZLViewWidget.Angle.DEGREES0:
			default:
				return myBindings0;
			case ZLViewWidget.Angle.DEGREES90:
				return myBindings90;
			case ZLViewWidget.Angle.DEGREES180:
				return myBindings180;
			case ZLViewWidget.Angle.DEGREES270:
				return myBindings270;
		}
	}

	FBView getTextView() {
		return (FBView)getCurrentView();
	}

	int getMode() {
		return myMode;
	}

	void setMode(int mode) {
		if (mode == myMode) {
			return;
		}

		myPreviousMode = myMode;
		myMode = mode;

		switch (mode) {
			case ViewMode.BOOK_TEXT:
				setView(BookTextView);
				break;
			case ViewMode.FOOTNOTE:
				setView(FootnoteView);
				break;
			default:
				break;
		}
	}

	void restorePreviousMode() {
		setMode(myPreviousMode);
		myPreviousMode = ViewMode.BOOK_TEXT;
	}

	void tryOpenFootnote(String id) {
		if (Model != null) {
			BookModel.Label label = Model.getLabel(id);
			if ((label != null) && (label.Model != null)) {
		//		if (label.Model == Model.BookTextModel) {
				if (label.ModelIndex != -1) {	
					BookTextView.gotoParagraphSafe(label.ModelIndex, label.ParagraphIndex);
				} else {
					FootnoteView.setModel(label.Model);
					setMode(ViewMode.FOOTNOTE);
					FootnoteView.gotoParagraph(label.ParagraphIndex, false);
				}
				setHyperlinkCursor(false);
				refreshWindow();
			}
		}
	}

	public void clearTextCaches() {
		BookTextView.clearCaches();
		FootnoteView.clearCaches();
	}
	
	void openBookInternal(BookDescription description) {
		clearTextCaches();

		if (description != null) {
			BookTextView.saveState();
			BookTextView.setModels(null, "");

			Model = null;
			Model = new BookModel(description);
			final String fileName = description.FileName;
			myBookNameOption.setValue(fileName);
			ZLTextHyphenator.Instance().load(description.getLanguage());
			BookTextView.setModels(Model.getBookTextModels(), fileName);
			BookTextView.setCaption(description.getTitle());
			FootnoteView.setModel(null);
			FootnoteView.setCaption(description.getTitle());
			RecentBooks.Instance().addBook(fileName);
		}
		resetWindowCaption();
		refreshWindow();
	}
	
	void showBookTextView() {
		setMode(ViewMode.BOOK_TEXT);
	}
	
	private class OpenBookRunnable implements Runnable {
		private	BookDescription myDescription;

		public OpenBookRunnable(BookDescription description) { 
			myDescription = description; 
		}
		
		public void run() { 
			openBookInternal(myDescription); 
		}
	}

	@Override
	public boolean openFile(String fileName) {
		if (fileName == null) {
			return false;
		}
		BookDescription description = BookDescription.getDescription(fileName);
		if (description == null) {
			final ZLFile file = new ZLFile(fileName);
			if (file.isArchive()) {
				final ZLDir directory = file.getDirectory();
				if (directory != null) {
					final ArrayList items = directory.collectFiles();
					final int size = items.size();
					for (int i = 0; i < size; ++i) {
						final String itemFileName = directory.getItemPath((String)items.get(i));
						description = BookDescription.getDescription(itemFileName);
						if (description != null) {
							break;
						}
					}
				}
			}
		}
		if (description != null) {
			openBook(description);
			refreshWindow();
			return true;
		} else {
			return false;
		}
	}

	public void onWindowClosing() {
		if (BookTextView != null) {
			BookTextView.saveState();
		}
	}
}
