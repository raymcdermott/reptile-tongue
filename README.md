# repl-ui

A [re-frame](https://github.com/Day8/re-frame) shared REPL

## Features

- [X] Multi-user REPL
- [X] Real-time keystrokes from all connected users
- [X] Shared, accessible history
- [X] Authenticated server access
- [X] Color syntax for output
- [X] Basic parinfer integration
- [X] Simple exception viewing 
- [X] Add a library on demand
- [ ] Incremental feedback on long running REPL evaluations
- [ ] Cancel long running REPL evaluations
- [ ] Per user name spaces
- [ ] Filtered / expandable exception viewing 
- [ ] Color syntax whilst editing
- [ ] Show matching / balancing parens
 

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
