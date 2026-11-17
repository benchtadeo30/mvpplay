package com.appdev.mvpplay;

import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

// "Create podcast" and "Add episode": pick an MP4, fill in the details, save.
// Create ONE of these in a field of the activity (see the steps).
public class PodcastHelper {

    private final AppCompatActivity activity;
    private final Runnable onChanged;   // called after something was saved

    private int targetShowId = -1;      // -1 = creating a new podcast
    private Uri pendingThumb;
    private ImageView dialogThumb;

    private final ActivityResultLauncher<String[]> videoPicker;
    private final ActivityResultLauncher<String> imagePicker;

    public PodcastHelper(AppCompatActivity activity, Runnable onChanged) {
        this.activity = activity;
        this.onChanged = onChanged;

        videoPicker = activity.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) showDialog(uri);
                });
        imagePicker = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        pendingThumb = uri;
                        if (dialogThumb != null) dialogThumb.setImageURI(uri);
                    }
                });
    }

    // true when a podcast (official or yours) already has this name
    private boolean nameTaken(String title) {
        for (PodcastShows.Show s : PodcastShows.group(activity,
                MusicLibrary.loadPodcasts(activity))) {
            if (s.name.equalsIgnoreCase(title)) return true;
        }
        return false;
    }

    // new podcast: pick the first video
    public void createPodcast() {
        targetShowId = -1;
        videoPicker.launch(new String[]{"video/mp4"});
    }

    // new episode for a podcast you made
    public void addEpisode(int showId) {
        targetShowId = showId;
        videoPicker.launch(new String[]{"video/mp4"});
    }

    private void showDialog(final Uri video) {
        final boolean creating = targetShowId < 0;
        final int showId = targetShowId;
        pendingThumb = null;

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_add_podcast, null);
        final TextView header = view.findViewById(R.id.tvPodHeader);
        final EditText etTitle = view.findViewById(R.id.etPodTitle);
        final EditText etEpisode = view.findViewById(R.id.etPodEpisode);
        final EditText etDesc = view.findViewById(R.id.etPodDesc);
        final TextView btnSave = view.findViewById(R.id.btnPodSave);
        dialogThumb = view.findViewById(R.id.ivPodThumb);

        header.setText(creating ? "Create your podcast" : "Add episode");
        etTitle.setHint(creating ? "Podcast title" : "Episode title");
        etEpisode.setVisibility(creating ? View.VISIBLE : View.GONE);
        etDesc.setHint(creating ? "Description" : "Episode description");

        view.findViewById(R.id.btnPodThumb).setOnClickListener(v -> imagePicker.launch("image/*"));

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setOnDismissListener(d -> dialogThumb = null);
        view.findViewById(R.id.btnPodCancel).setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            final String title = etTitle.getText().toString().trim();
            final String description = etDesc.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(activity, "Type a title first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (creating && nameTaken(title)) {
                Toast.makeText(activity, "You already have a podcast with that name",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String firstEpisode = etEpisode.getText().toString().trim();
            final String episodeTitle = creating
                    ? (firstEpisode.isEmpty() ? "Episode 1" : firstEpisode)
                    : title;
            final Uri thumb = pendingThumb;

            btnSave.setEnabled(false);
            btnSave.setText("Saving...");
            new Thread(() -> {
                android.content.Context app = activity.getApplicationContext();
                boolean ok;
                if (creating) {
                    int newId = CustomPodcastStore.createShow(app, title, description, thumb);
                    ok = newId >= 0 && CustomPodcastStore.addEpisode(
                            app, newId, video, episodeTitle, description, thumb);
                    if (!ok && newId >= 0) CustomPodcastStore.deleteShow(app, newId);
                } else {
                    ok = CustomPodcastStore.addEpisode(
                            app, showId, video, episodeTitle, description, thumb);
                }
                final boolean saved = ok;
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    if (saved) {
                        Toast.makeText(activity, creating ? "Podcast created" : "Episode added",
                                Toast.LENGTH_SHORT).show();
                        if (onChanged != null) onChanged.run();
                    } else {
                        Toast.makeText(activity, "Couldn't add that video",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
        dialog.show();
    }
}