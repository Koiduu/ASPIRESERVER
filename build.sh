#!/bin/bash
# Build all ASPIRESERVER plugins
# Output JARs will be in each module's target/ folder and also copied to build/

set -e

echo "=== Building ASPIRESERVER ==="
./mvnw clean package -q

mkdir -p build

echo ""
echo "=== Copying JARs to build/ ==="
cp aspire-core/target/aspire-core-*.jar build/
cp aspire-buildbattle/target/aspire-buildbattle-*.jar build/
cp aspire-smp/target/aspire-smp-*.jar build/
cp aspire-creative/target/aspire-creative-*.jar build/

echo ""
echo "=== Build Complete! ==="
echo "JARs ready in build/:"
ls -1 build/*.jar
echo ""
echo "Copy all JARs from build/ into your server's plugins/ folder."
