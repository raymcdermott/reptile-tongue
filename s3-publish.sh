#!/usr/bin/env bash

# Adapt to use your own bucket if you wish to serve yourself

set -ex

lein cljsbuild once

aws s3 sync ./resources/public s3://reptile-ui.extemporay.io/ --delete
