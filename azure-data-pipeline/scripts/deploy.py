"""
deploy.py — Telecom Azure Data Pipeline Setup
"""

import os
import time
from azure.identity import DefaultAzureCredential
from azure.mgmt.storage import StorageManagementClient
from azure.mgmt.datafactory import DataFactoryManagementClient
from azure.mgmt.datafactory.models import (
    Factory,
    LinkedServiceResource,
    AzureBlobStorageLinkedService,
    DatasetResource,
    AzureBlobDataset,
    TextFormat,
    LinkedServiceReference,
    PipelineResource,
    CopyActivity,
    DatasetReference,
    BlobSource,
    BlobSink,
)
from azure.storage.blob import BlobServiceClient

SUBSCRIPTION_ID = "d4dd2968-d53c-497d-b767-e6c95be4326d"
RESOURCE_GROUP  = "telecom-rg"
LOCATION        = "canadaeast"
STORAGE_ACCOUNT = "telecomstore001guc"
CONTAINER_NAME  = "subscription-data"
ADF_NAME        = "telecom-adf-001"
PIPELINE_NAME   = "CopySubscriptionData"
LOCAL_CSV       = os.path.join(os.path.dirname(__file__), "..", "data", "subscriptions.csv")

print("=== Telecom Azure Data Pipeline Setup ===\n")
credential = DefaultAzureCredential()

# Step 1: Storage Account
print("[1/5] Creating Storage Account...")
storage_client = StorageManagementClient(credential, SUBSCRIPTION_ID)
try:
    op = storage_client.storage_accounts.begin_create(
        RESOURCE_GROUP, STORAGE_ACCOUNT,
        {"location": LOCATION, "kind": "StorageV2", "sku": {"name": "Standard_LRS"}}
    )
    op.result()
except Exception:
    pass  # already exists
print(f"      Done: {STORAGE_ACCOUNT}")

keys = storage_client.storage_accounts.list_keys(RESOURCE_GROUP, STORAGE_ACCOUNT)
account_key = keys.keys[0].value
conn_str = (
    f"DefaultEndpointsProtocol=https;AccountName={STORAGE_ACCOUNT};"
    f"AccountKey={account_key};EndpointSuffix=core.windows.net"
)

# Step 2: Upload CSV
print("[2/5] Uploading subscriptions.csv...")
blob_svc = BlobServiceClient.from_connection_string(conn_str)
try:
    blob_svc.create_container(CONTAINER_NAME)
except Exception:
    pass
with open(LOCAL_CSV, "rb") as f:
    blob_svc.get_blob_client(CONTAINER_NAME, "raw/subscriptions.csv").upload_blob(f, overwrite=True)
print(f"      Uploaded → {CONTAINER_NAME}/raw/subscriptions.csv")

# Step 3: Data Factory
print("[3/5] Creating Azure Data Factory...")
adf = DataFactoryManagementClient(credential, SUBSCRIPTION_ID)
try:
    adf.factories.create_or_update(RESOURCE_GROUP, ADF_NAME, Factory(location=LOCATION))
except Exception:
    pass
print(f"      Done: {ADF_NAME}")
time.sleep(15)

# Step 4: Linked Service + Datasets
print("[4/5] Creating Linked Service and Datasets...")
LS_NAME = "BlobStorageLS"

adf.linked_services.create_or_update(
    RESOURCE_GROUP, ADF_NAME, LS_NAME,
    LinkedServiceResource(
        properties=AzureBlobStorageLinkedService(connection_string=conn_str)
    )
)

ls_ref = LinkedServiceReference(
    reference_name=LS_NAME,
    type="LinkedServiceReference"
)

adf.datasets.create_or_update(
    RESOURCE_GROUP, ADF_NAME, "SourceCSV",
    DatasetResource(
        properties=AzureBlobDataset(
            linked_service_name=ls_ref,
            folder_path=f"{CONTAINER_NAME}/raw",
            file_name="subscriptions.csv",
            format=TextFormat(column_delimiter=",", first_row_as_header=True)
        )
    )
)

adf.datasets.create_or_update(
    RESOURCE_GROUP, ADF_NAME, "SinkCSV",
    DatasetResource(
        properties=AzureBlobDataset(
            linked_service_name=ls_ref,
            folder_path=f"{CONTAINER_NAME}/processed",
            file_name="subscriptions_processed.csv",
            format=TextFormat(column_delimiter=",", first_row_as_header=True)
        )
    )
)

# Step 5: Pipeline
print("[5/5] Creating and running pipeline...")

adf.pipelines.create_or_update(
    RESOURCE_GROUP, ADF_NAME, PIPELINE_NAME,
    PipelineResource(
        activities=[
            CopyActivity(
                name="CopyCSV",
                inputs=[DatasetReference(reference_name="SourceCSV", type="DatasetReference")],
                outputs=[DatasetReference(reference_name="SinkCSV", type="DatasetReference")],
                source=BlobSource(),
                sink=BlobSink(),
            )
        ]
    )
)

run = adf.pipelines.create_run(RESOURCE_GROUP, ADF_NAME, PIPELINE_NAME)
print(f"      Pipeline triggered! Run ID: {run.run_id}")

print("\n=== Setup Complete ===")
print(f"Storage : portal.azure.com → Storage accounts → {STORAGE_ACCOUNT}")
print(f"ADF     : adf.azure.com → {ADF_NAME}")