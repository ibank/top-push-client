#!/bin/bash
rm -rf packages
mono --runtime=v4.0 ../../work-tool/NuGet.exe install packages.config -NoCache -o packages
