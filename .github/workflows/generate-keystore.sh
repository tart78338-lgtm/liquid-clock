#!/bin/bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias liquid_organism_key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass liquid_organism_pass \
  -keypass liquid_organism_pass \
  -dname "CN=LiquidOrganism, OU=Dev, O=App, L=World, S=State, C=US"
