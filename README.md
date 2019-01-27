# pollylog

## requirements

For the deployment on polly a java runtime environment version 8 or higher is required.
The development setup requires leinigen: <https://leiningen.org/>

## Quickstart

On windows run the `start.bat`, the backend will open in cmd shell, and the standardbrowser will
head to <http://localhost:31514>.



## development
```
# run backend server
lein run
# and interactive frontend
lein figwheel dev
```	

## build
```
# build the clojurescript frontend
lein clean
lein cljsbuild once min
# pack into .jar file
lein uberjar
# copy the uberjar to to `pollylog_produciton` and zip
cp .\target\uberjar\pollylog-0.1.1-standalone.jar .\pollylog_production\
...
```


## License
Copyright 2019, Martin Radenz, MIT License
