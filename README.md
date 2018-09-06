# reptile-tongue

A [re-frame](https://github.com/Day8/re-frame) based SPA for the REPtiLE shared REPL

## Features

- [X] Multi-user REPL
- [X] Real-time keystrokes from all connected users
- [X] Shared, accessible history
- [X] Authenticated server access
- [X] Color syntax for output
- [X] Parinfer integration
- [X] Simple exception viewing 
- [X] Add a library on demand (Maven & Git SHAs)
- [ ] Filtered / expandable exception viewing 
- [X] Color syntax whilst editing
- [ ] Show matching / balancing parens
- [ ] Per user name spaces
- [ ] Incremental feedback on long running REPL evaluations
- [ ] Cancel long running REPL evaluations
- [ ] Choice of editor key mappings

## Configuration

The location of the back-end server is configurable.

TBD

## Development

### Run application:

```
clojure -A:fig -b dev -r
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:9500](http://localhost:9500).

## Production Build

TBD
