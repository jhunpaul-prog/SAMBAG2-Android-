//package com.example.emergency;
//
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.provider.Settings;
//import android.view.View;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.AppCompatButton;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//public class Permission extends AppCompatActivity {
//
//    AppCompatButton takepermission;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState){
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.fragment_emergencyform);
//        takepermission=findViewById(R.id.browse_button);
//        takepermission.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                //Permission for sdk between 23 and 29
//                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
//                    if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
//                        ActivityCompat.requestPermissions( MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
//                    }
//                }
//                //Permission for sdk 30 and above
//                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
//                    if(!Environment.isExternalStorageManager()){
//                        try{
//                            Intent intent=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
//                            intent.addCategory("android.intent.category.DEFAULT");
//                            intent.setData(Uri.parse(String.format("package:%s",getApplicationContext().getPackageManager())));
//                            startActivityIfNeeded(intent, 101);
//                        }catch (Exception exception){
//                            Intent intent = new Intent();
//                            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                            startActivityIfNeeded(intent, 101);
//                        }
//                    }
//                }
//            }
//        });
//    }
//}
