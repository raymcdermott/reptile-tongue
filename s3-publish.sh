#!/usr/bin/env bash

# Adapt to use your own bucket if you wish to serve yourself

set -ex

rm -rf ./resources/public/js

clojure -A:fig:min

rm -rf ./resources/public/js/compiled/out

aws s3 sync ./resources/public s3://reptile-ui.extemporay.io/ --delete
