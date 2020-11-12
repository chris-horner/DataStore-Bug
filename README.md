# DataStore Bug

This is a tiny little project to demonstrate what might be an issue with Google's [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) library.

It attempts to make use of [Okio](https://square.github.io/okio)'s `CipherSource` and `CipherSink` to encrypt and decrypt the data saved inside of a `DataStore`.

However something goes wrong when attempting to write the data to disk. `CipherSink` needs to close the `OutputStream` that's provided in `Serializer`'s `writeTo()` method, however this causes DataStore to crash, throwing the following exception:
```
java.io.SyncFailedException: sync failed
    at java.io.FileDescriptor.sync(Native Method)
    at androidx.datastore.SingleProcessDataStore.writeData$datastore_core_release(SingleProcessDataStore.kt:299)
    at androidx.datastore.SingleProcessDataStore.transformAndWrite(SingleProcessDataStore.kt:282)
    at androidx.datastore.SingleProcessDataStore$actor$1.invokeSuspend(SingleProcessDataStore.kt:165)
```

Which is the result of `SingleProcessDataStore` attempting to call:
```kotlin
stream.fd.sync()
```
