#!/bin/sh

#
#  Copyright (C) 2025 Volt Active Data Inc.
# 
#  Use of this source code is governed by an MIT
#  license that can be found in the LICENSE file or at
#  https://opensource.org/licenses/MIT.
# 

. $HOME/.profile

sudo apt install -y maven

cd
mkdir logs 2> /dev/null

cd ../scripts
$HOME/bin/reload_dashboards.sh tatp.json

java  ${JVMOPTS}  -jar $HOME/bin/addtodeploymentdotxml.jar `cat $HOME/.vdbhostnames`  deployment $HOME/voltdb-charglt/scripts/export_and_import.xml

