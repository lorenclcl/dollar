#!/bin/bash -eux
cd $(dirname $0)
DIR=$(pwd)

echo "login=neilellis" > ~/.github
echo "password=${GITHUB_PASSWORD}" >> ~/.github

[ -d target/build_test ] || mkdir target/build_test
cp $DIR/dollar-examples/src/main/resources/test_*.ds  target/build_test
for file in $(ls target/build_test)
do
    echo "Testing: " $file
    $DIR/dist/dollar/bin/dollar target/build_test/${file}
done