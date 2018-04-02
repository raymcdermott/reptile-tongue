# repl-ui

A [re-frame](https://github.com/Day8/re-frame) shared REPL

## Features

[x] Shared REPL
[x] Color syntax for output
[x] Color syntax whilst editing
[x] Shared REPL

 

## Development Mode

### Run application:

```
lein clean
lein figwheel dev
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

## Production Build


To compile clojurescript to javascript:

```
lein clean
lein cljsbuild once min
```
