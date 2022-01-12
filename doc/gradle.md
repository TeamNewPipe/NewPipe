# Custom gradle parameters

You can use these parameters by specifying them inside the `gradle.properties` file as 
`systemProp.<name>=<value>` or passing them through the CLI with `-D<name>=<value>`.

## packageSuffix
This allows you to specify a suffix, which will be added on release builds to the application id, 
the `app_name` string and the apk file.  
No validation is made, so make sure to pass in a valid value.

## skipFormatKtlint
This allows you to skip the `formatKtLint` task.  
No value is needed.   
It is used for CI in order to check for badly formatted files.
