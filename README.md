# repl-ui

A [re-frame](https://github.com/Day8/re-frame) shared REPL

## Features

- [X] Shared REPL
- [X] Color syntax for output
- [X] simple parinfer integration
- [ ] Simple exception viewing 
- [ ] Filtered / expandable exception viewing 
- [ ] Color syntax whilst editing
- [ ] user selected parinfer mode
- [ ] matching parens

 

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
