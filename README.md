# reptile-tongue

A [re-frame](https://github.com/Day8/re-frame) based SPA for the REPtiLE shared REPL

## Features

- [X] Multi-user REPL
- [X] Real-time keystrokes from all connected users
- [X] Shared, accessible history
- [X] Authenticated server access
- [X] Colorised edits / output
- [X] Parinfer integration
- [X] Show matching / balancing parens
- [X] Add a library on demand (Maven & Git SHAs)
- [ ] Code completion / suggestions 
- [ ] Expose function docstring documentation
- [ ] Expandable exception viewing 
- [ ] Per user name spaces
- [ ] Incremental feedback on long running REPL evaluations
- [ ] Cancel long running REPL evaluations
- [ ] Choice of editor key mappings

## Development

### Run application:

```
clojure -A:fig:build
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:9500](http://localhost:9500).

The REPtiLe server will be started in the background so there is no need to start an additional server in the development process.

## Configuration

The location of the back-end server is configurable.

The name of the server can be set in the `min.cljs.edn` by changing `reptile.tongue.config/TAIL_SERVER`


## Production Build

```
clojure -A:fig:min
```

#### AWS S3 hosting

AWS S3 buckets can be configured to host web sites and thus be used to serve the UI.

The `s3-publish.sh` script is provided for building the code and syncing with an S3 bucket. 

The script will also invalidate any configured CloudFront distribution.
