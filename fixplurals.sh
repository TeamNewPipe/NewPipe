#!/bin/bash

javac CheckTranslations.java
find app/src -name "*.xml" | grep values | xargs java CheckTranslations -r
