#!/bin/bash

mvn package && gcloud functions deploy lunchmoney-alerts \
  --gen2 \
  --runtime=java21 \
  --region=us-west1 \
  --source=target/deployment \
  --entry-point org.dalquist.lunchmoney.alerts.BalanceAlert \
  --memory=256MB \
  --trigger-topic lunchmoney-alert-topic