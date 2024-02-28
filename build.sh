#!/bin/bash

ROOT=$(dirname $0)
cd "$ROOT"

echo "Building project..."
mvn clean package || exit 1

SERVICE_NAME="gigachat-ml-service"
BUILD_BRANCH=$(git rev-parse --abbrev-ref HEAD)
BRANCH_NAME_LOWER=$(echo "$BUILD_BRANCH" | tr '[:upper:]' '[:lower:]')

## Other environment
#SECRET=$(LC_CTYPE=C tr -dc 'a-z0-9' </dev/urandom | head -c 24)
SECRET=p8smccsnrr6ogzamx6sx7bfp

# Linux
#SECRET_FILE=~/.mlp-secret-$SERVICE_NAME
#if [[ ! -e $SECRET_FILE ]]; then
#    echo $(tr -dc a-z0-9 </dev/urandom | head -c 24) > $SECRET_FILE
#fi
#SECRET=$(cat $SECRET_FILE)

IMAGE=docker-pub.caila.io/caila-public/$SERVICE_NAME-$SECRET:$BRANCH_NAME_LOWER

DOCKER_BUILDKIT=1 docker build . -t "$IMAGE"

echo "$IMAGE"

docker login docker-pub.caila.io -u 'robot$caila-public' -p 'gaerrvb54zjvRx8klacJjh905QZ4Gw7L'
docker push "$IMAGE"

echo --------------------------------------------------
echo Docker image: $IMAGE
echo --------------------------------------------------