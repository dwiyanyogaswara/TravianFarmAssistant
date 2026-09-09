package com.example.travianfarmassistant
import android.app.*; import android.content.*; import android.net.Uri; import android.os.Bundle; import android.widget.*
class MainActivity:Activity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
  val s=findViewById<EditText>(R.id.server); val u=findViewById<EditText>(R.id.username)
  val st=findViewById<TextView>(R.id.status); val nr=findViewById<TextView>(R.id.nextRun)
  val i=findViewById<Spinner>(R.id.interval); val d=findViewById<Spinner>(R.id.duration)
  val p=getSharedPreferences("config",0); s.setText(p.getString("server","https://ts20.x2.europe.travian.com"));u.setText(p.getString("username",""))
  i.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("5 menit","10 menit","15 menit","30 menit","60 menit"))
  d.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("1 jam","6 jam","12 jam","24 jam"))
  findViewById<Button>(R.id.start).setOnClickListener{p.edit().putString("server",s.text.toString()).putString("username",u.text.toString()).apply();st.text="Status: RUNNING";nr.text="Next run: interval berikutnya";startService(Intent(this,ReminderService::class.java).putExtra("minutes",listOf(5,10,15,30,60)[i.selectedItemPosition]).putExtra("hours",listOf(1,6,12,24)[d.selectedItemPosition]))}
  findViewById<Button>(R.id.stop).setOnClickListener{stopService(Intent(this,ReminderService::class.java));st.text="Status: STOPPED";nr.text="Next run: --"}
  findViewById<Button>(R.id.openFarm).setOnClickListener{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(s.text.toString().ifBlank{"https://ts20.x2.europe.travian.com"})))}
 }}
class ReminderService:Service(){
 override fun onStartCommand(x:Intent?,f:Int,id:Int):Int{
  val mins=x?.getIntExtra("minutes",15)?:15; val hours=x?.getIntExtra("hours",24)?:24
  val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("farm","Farm reminders",NotificationManager.IMPORTANCE_DEFAULT))
  Thread{val end=System.currentTimeMillis()+hours*3600000L;while(System.currentTimeMillis()+mins*60000L<end){Thread.sleep(mins*60000L);nm.notify((System.currentTimeMillis()%100000).toInt(),Notification.Builder(this,"farm").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Travian Farm Assistant").setContentText("Waktunya farming: buka Farm List dan tekan Send All.").setAutoCancel(true).build())};stopSelf()}.start();return START_STICKY}
 override fun onBind(i:Intent?)=null
}