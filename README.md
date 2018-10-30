# reptile-tongue

A [re-frame](https://github.com/Day8/re-frame) based SPA for the [REPtiLE shared REPL](https://github.com/raymcdermott/reptile-tail)

## Features

### REPtiLe connectivity
- [X] Multi-user REPL connectivity
- [X] Authenticated server access
- [X] Real-time keystrokes from all connected users

### Other editors
- [X] Visibility controls

### Clojure code support
- [X] Parinfer integration
- [X] Colorised edits / output
- [X] Show matching / balancing parens
- [ ] Code completion / suggestions 
- [ ] Expose function documentation
- [ ] Choice of editor key mappings

### Clojure evaluation
- [X] Shared REPL state
- [X] Shared, accessible history
- [X] Friendly exceptions 
- [ ] Incremental feedback on long running REPL evaluations
- [ ] Cancel long running REPL evaluations

### Deps.edn
- [X] Add a library on demand (Maven & Git SHAs)

### Security
- [X] Secure REPtiLe server connection

### REPtiLe developer features
- [X] Live reloadable client code
- [X] Live reloadable server code

### User features under consideration post 1.0
- [ ] Per user name spaces
- [ ] Automatic editor visibility based on activity
- [ ] OpenID Connect Services eg GitHub


## Development

### Run application:

```
clojure -A:fig:build
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:9500](http://localhost:9500).

The REPtiLe server will be started in the background so there is no need to start a separate server in the development process.

## Configuration

The location of the back-end server is configurable.

The name of the server can be set in the `min.cljs.edn` by changing `reptile.tongue.config/TAIL_SERVER`

Please also checkout the [REPtiLE configuration](https://github.com/raymcdermott/reptile-tail) options

## Production Build

```
clojure -A:fig:min
```

#### AWS S3 hosting

AWS S3 buckets can be configured to host web sites and thus be used to serve the UI.

The `s3-publish.sh` script is provided for building the code and syncing with an S3 bucket. 

The script will also invalidate any configured CloudFront distribution.
