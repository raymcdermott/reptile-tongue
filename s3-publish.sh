#!/usr/bin/env bash

# Adapt to use your own bucket / CF Distribution if you wish to serve yourself

S3_BUCKET=s3://reptile-ui.extemporay.io/
CF_DISTRIBUTION=E34RC3TC9Z6717

# Adapt to your own host name / port for the server

REPTILE_TAIL_SERVER="reptile.extemporay.io"

set -ex

rm -rf ./resources/public/js

clojure -A:fig:min

rm -rf ./resources/public/js/compiled/out

aws s3 sync ./resources/public ${S3_BUCKET} --delete

aws cloudfront create-invalidation --distribution-id ${CF_DISTRIBUTION} --paths /js/compiled/app.js
