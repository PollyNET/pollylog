# pollylog

## requirements

For the deployment on polly a java runtime environment version 8 or higher is required.


## Quickstart

On windows run the `start.bat`, the backend will open in cmd shell, and the standard browser will
head to <http://localhost:31514>.
The logbook entries are stored in a fixed format in a csv file.
Adapt the port, filename and channels in the `config.json` file.

Following an update it might be necessary to clear the cache of the local browser.

## development
The development setup requires leinigen: <https://leiningen.org/>

```
# run backend server
lein run

# and interactive frontend
lein figwheel dev
```	

switch into the dev namespace
```
(in-ns 'pollylogbrowser.core)
```

## build
```
# build the clojurescript frontend
lein clean
lein cljsbuild once min
# pack into .jar file
lein uberjar
# copy the uberjar to to `pollylog_production` and zip
cp .\target\uberjar\pollylog-*-standalone.jar .\pollylog_production\
...
```

## License
Copyright 2021, Martin Radenz, MIT License
