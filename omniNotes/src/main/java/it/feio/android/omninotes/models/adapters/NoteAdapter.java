/*
 * Copyright (C) 2013-2022 Federico Iosue (federico@iosue.it)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.feio.android.omninotes.models.adapters;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static it.feio.android.omninotes.utils.ConstantsBase.TIMESTAMP_UNIX_EPOCH_FAR;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Spanned;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.pixplicity.easyprefs.library.Prefs;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.async.TextWorkerTask;
import it.feio.android.omninotes.helpers.LogDelegate;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.holders.NoteViewHolder;
import it.feio.android.omninotes.utils.BitmapHelper;
import it.feio.android.omninotes.utils.Navigation;
import it.feio.android.omninotes.utils.TextHelper;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;


public class NoteAdapter extends RecyclerView.Adapter<NoteViewHolder> {

  private final Activity mActivity;
  private final int navigation;
  private final List<Note> notes;
  private final SparseBooleanArray selectedItems = new SparseBooleanArray();
  private final boolean expandedView;
  private long closestNoteReminder = Long.parseLong(TIMESTAMP_UNIX_EPOCH_FAR);
  private int closestNotePosition;
  private ColorManager colorManager = new ColorManager();


  public NoteAdapter(Activity activity, boolean expandedView, List<Note> notes) {
    this.mActivity = activity;
    this.notes = notes;
    this.expandedView = expandedView;
    navigation = Navigation.getNavigation();
    manageCloserNote(notes, navigation);
  }


  /**
   * Highlighted if is part of multiselection of notes. Remember to search for child with card ui
   */
  private void manageSelectionColor(int position, Note note, NoteViewHolder holder) {
    if (selectedItems.get(position)) {
      holder.cardLayout
          .setBackgroundColor(mActivity.getResources().getColor(R.color.list_bg_selected));
    } else {
      colorManager.restoreDrawable(note, holder.cardLayout, holder);
    }
  }


  private void initThumbnail(Note note, NoteViewHolder holder) {

    if (expandedView && holder.attachmentThumbnail != null) {
      // If note is locked or without attachments nothing is shown
      if ((note.isLocked() && !Prefs.getBoolean("settings_password_access", false))
          || note.getAttachmentsList().isEmpty()) {
        holder.attachmentThumbnail.setVisibility(View.GONE);
      } else {
        holder.attachmentThumbnail.setVisibility(View.VISIBLE);
        Attachment mAttachment = note.getAttachmentsList().get(0);
        Uri thumbnailUri = BitmapHelper.getThumbnailUri(mActivity, mAttachment);

        Glide.with(mActivity)
            .load(thumbnailUri)
            .apply(new RequestOptions().centerCrop())
            .transition(withCrossFade())
            .into(holder.attachmentThumbnail);
      }
    }
  }


  public List<Note> getNotes() {
    return notes;
  }


  private void initDates(Note note, NoteViewHolder holder) {
    String dateText = TextHelper.getDateText(mActivity, note, navigation);
    holder.date.setText(dateText);
  }


  private void initIcons(Note note, NoteViewHolder holder) {
    // Evaluates the archived state...
    holder.archiveIcon.setVisibility(note.isArchived() ? View.VISIBLE : View.GONE);
    // ...the location
    holder.locationIcon
        .setVisibility(note.getLongitude() != null && note.getLongitude() != 0 ? View.VISIBLE :
            View.GONE);

    // ...the presence of an alarm
    holder.alarmIcon.setVisibility(note.getAlarm() != null ? View.VISIBLE : View.GONE);
    // ...the locked with password state
    holder.lockedIcon.setVisibility(note.isLocked() ? View.VISIBLE : View.GONE);
    // ...the attachment icon for contracted view
    if (!expandedView) {
      holder.attachmentIcon
          .setVisibility(!note.getAttachmentsList().isEmpty() ? View.VISIBLE : View.GONE);
    }
  }


  private void initText(Note note, NoteViewHolder holder) {
    try {
      if (note.isChecklist()) {
        TextWorkerTask task = new TextWorkerTask(mActivity, holder.title, holder.content,
            expandedView);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, note);
      } else {
        Spanned[] titleAndContent = TextHelper.parseTitleAndContent(mActivity, note);
        holder.title.setText(titleAndContent[0]);
        holder.content.setText(titleAndContent[1]);
        holder.title.setText(titleAndContent[0]);
        if (titleAndContent[1].length() > 0) {
          holder.content.setText(titleAndContent[1]);
          holder.content.setVisibility(View.VISIBLE);
        } else {
          holder.content.setVisibility(View.INVISIBLE);
        }
      }
    } catch (RejectedExecutionException e) {
      LogDelegate.w("Oversized tasks pool to load texts!", e);
    }
  }


  /**
   * Saves the position of the closest note to align list scrolling with it on start
   */
  private void manageCloserNote(List<Note> notes, int navigation) {
    if (navigation == Navigation.REMINDERS) {
      for (int i = 0; i < notes.size(); i++) {
        long now = Calendar.getInstance().getTimeInMillis();
        long reminder = Long.parseLong(notes.get(i).getAlarm());
        if (now < reminder && reminder < closestNoteReminder) {
          closestNotePosition = i;
          closestNoteReminder = reminder;
        }
      }
    }

  }


  /**
   * Returns the note with the nearest reminder in the future
   */
  public int getClosestNotePosition() {
    return closestNotePosition;
  }


  public SparseBooleanArray getSelectedItems() {
    return selectedItems;
  }


  public void addSelectedItem(Integer selectedItem) {
    selectedItems.put(selectedItem, true);
  }


  public void removeSelectedItem(Integer selectedItem) {
    selectedItems.delete(selectedItem);
  }


  public void clearSelectedItems() {
    selectedItems.clear();
  }


  public void restoreDrawable(Note note, View v) {
    colorManager.restoreDrawable(note, v, null);
  }

  public void replace(@NonNull Note note, int index) {
    if (notes.contains(note)) {
      remove(note);
    } else {
      index = notes.size();
    }
    add(index, note);
  }

  public void add(int index, @NonNull Object o) {
    notes.add(index, (Note) o);
    notifyItemInserted(index);
  }

  public void remove(List<Note> notes) {
    for (Note note : notes) {
      remove(note);
    }
  }

  public void remove(@NonNull Note note) {
    int pos = getPosition(note);
    if (pos >= 0) {
      notes.remove(note);
      notifyItemRemoved(pos);
    }
  }

  public int getPosition(@NonNull Note note) {
    return notes.indexOf(note);
  }

  public Note getItem(int index) {
    return notes.get(index);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @NonNull
  @Override
  public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view;
    if (expandedView) {
      view = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.note_layout_expanded, parent, false);
    } else {
      view = LayoutInflater.from(parent.getContext()).inflate(R.layout.note_layout, parent, false);
    }

    return new NoteViewHolder(view, expandedView);
  }

  @Override
  public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
    Note note = notes.get(position);
    initText(note, holder);
    initIcons(note, holder);
    initDates(note, holder);
    initThumbnail(note, holder);
    manageSelectionColor(position, note, holder);
  }

  @Override
  public int getItemCount() {
    return this.notes.size();
  }

}