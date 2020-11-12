package codes.chrishorner.datastorebug

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.datastore.Serializer
import androidx.datastore.createDataStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.buffer
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity(R.layout.activity_main) {

  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope.launch {
      val firstStore = createDataStore("encrypted_string", EncryptedStringSerializer)
      val initialValue = firstStore.data.first()
      Log.d("DataStore", "initialValue = $initialValue")
      firstStore.updateData { "hello" }

      val secondStore = createDataStore("encrypted_string", EncryptedStringSerializer)
      val secondValue = secondStore.data.first()
      Log.d("DataStore", "secondValue = $secondValue")
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
  }
}

object EncryptedStringSerializer : Serializer<String> {

  override fun readFrom(input: InputStream): String {
    if (input.available() == 0) return ""

    return IoEncryption.getDecryptedSource(input).buffer().use {
      it.readString(Charsets.UTF_8)
    }
  }

  override fun writeTo(t: String, output: OutputStream) {
    // 🚨 It's this call that fails! 🚨
    IoEncryption.getEncryptedSink(output).buffer().use {
      it.writeString(t, Charsets.UTF_8)
    }
  }
}
