# DataStore Bug

This is a tiny little project to demonstrate issues found in [DataStore](https://developer.android.com/topic/libraries/architecture/datastore).

## Current Demo

`PreferenceDataStoreSingletonDelegate` and `DataStoreSingletonDelegate` leak `Context` in their `produceFile` lambdas.

To demonstrate, run the sample app and either rotate the screen or press the back button. LeakCanary will report the leak.
