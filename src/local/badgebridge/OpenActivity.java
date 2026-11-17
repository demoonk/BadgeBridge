package local.badgebridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Безоконная активность: открывает приложение, за которым следит значок.
 */
public class OpenActivity extends Activity {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String pkg = getIntent().getStringExtra("pkg");
        Intent launch = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);

        if (launch != null) {
            try {
                startActivity(launch);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show();
            }
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
