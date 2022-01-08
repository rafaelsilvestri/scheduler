#!/usr/bin/env bash

CONTAINER_NAME=redis_scheduler_lock
sudo docker exec -it $CONTAINER_NAME sh

# commands
# ping
# get [key]