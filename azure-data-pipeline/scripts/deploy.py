"""
deploy.py — One-shot Azure infrastructure setup for the Telecom Data Pipeline.

What this script does:
  1. Creates an Azure Storage Account + Blob container
  2. Uploads subscriptions.csv to Blob Storage
  3. Creates an Azure Data Factory instance
  4. Creates a pipeline: Blob Storage → Blob Storage (CSV copy, simulating MySQL export)

Prerequisites:
  pip install azure-identity azure-mgmt-storage azure-mgmt-datafactory azure-storage-blob

Usage:
  export AZURE_SUBSCRIPTION_ID=d4dd2968-d53c-497d-b767-e6c95be4326d
  python deploy.py
"""

import os
import time
from azure.identity import DefaultAzureCredential
from azure.mgmt.storage import StorageManagementClient
from azure.mgmt.datafactory import DataFactoryManagementClient
from azure.mgmt.datafactory.models import (
    Factory,
    LinkedService,
    AzureBlobStorageLinkedService,
    AzureBlobStorageLinkedServiceTypeProperties,
    DatasetResource,
    DelimitedTextDataset,
    DatasetReference,
    PipelineResource,
    CopyActivity,
    ActivityDependency,
    BlobSource,
    BlobSink,
    DelimitedTextReadSettings,
    DelimitedTextWriteSettings,
    AzureBlobDataset,
    LinkedServiceReference,
)
from azure.storage.blob import BlobServiceClient

# ── Configuration ─────────────────────────────────────────────────────────────
SUBSCRIPTION_ID   = os.environ.get("AZURE_SUBSCRIPTION_ID", "d4dd2968-d53c-497d-b767-e6c95be4326d")
RESOURCE_GROUP    = "telecom-rg"
LOCATION          = "canadaeast"
STORAGE_ACCOUNT   = "telecomstorage001"   # must be globally unique, lowercase, 3-24 chars
CONTAINER_NAME    = "subscription-data"
ADF_NAME          = "telecom-adf"
PIPELINE_NAME     = "CopySubscriptionData"
LOCAL_CSV         = os.path.join(os.path.dirname(__file__), "..", "data", "subscriptions.csv")

print("=== Telecom Azure Data Pipeline Setup ===\n")

credential = DefaultAzureCredential()

# ── Step 1: Create Storage Account ───────────────────────────────────────────
print("[1/5] Creating Storage Account...")
storage_client = StorageManagementClient(credential, SUBSCRIPTION_ID)

storage_async = storage_client.storage_accounts.begin_create(
    RESOURCE_GROUP,
    STORAGE_ACCOUNT,
    {
        "location": LOCATION,
        "kind": "StorageV2",
        "sku": {"name": "Standard_LRS"},
    }
)
storage_account = storage_async.result()
print(f"     Storage Account created: {storage_account.name}")

# Get connection string
keys = storage_client.storage_accounts.list_keys(RESOURCE_GROUP, STORAGE_ACCOUNT)
conn_str = (
    f"DefaultEndpointsProtocol=https;"
    f"AccountName={STORAGE_ACCOUNT};"
    f"AccountKey={keys.keys[0].value};"
    f"EndpointSuffix=core.windows.net"
)

# ── Step 2: Create Blob Container and Upload CSV ──────────────────────────────
print("[2/5] Creating Blob container and uploading subscriptions.csv...")
blob_service = BlobServiceClient.from_connection_string(conn_str)
container = blob_service.get_container_client(CONTAINER_NAME)

try:
    container.create_container()
except Exception:
    pass  # already exists

with open(LOCAL_CSV, "rb") as f:
    container.upload_blob("raw/subscriptions.csv", f, overwrite=True)

print(f"     Uploaded subscriptions.csv → {CONTAINER_NAME}/raw/subscriptions.csv")

# ── Step 3: Create Azure Data Factory ────────────────────────────────────────
print("[3/5] Creating Azure Data Factory...")
adf_client = DataFactoryManagementClient(credential, SUBSCRIPTION_ID)

adf = adf_client.factories.create_or_update(
    RESOURCE_GROUP,
    ADF_NAME,
    Factory(location=LOCATION)
)
print(f"     ADF created: {adf.name}")

# Wait for ADF to be ready
time.sleep(10)

# ── Step 4: Create Linked Service (Blob Storage connection) ───────────────────
print("[4/5] Creating Linked Service and Datasets...")
linked_service_name = "TelecomBlobStorage"

adf_client.linked_services.create_or_update(
    RESOURCE_GROUP,
    ADF_NAME,
    linked_service_name,
    LinkedService(
        properties=AzureBlobStorageLinkedService(
            connection_string=conn_str
        )
    )
)

# Source dataset (raw CSV)
adf_client.datasets.create_or_update(
    RESOURCE_GROUP,
    ADF_NAME,
    "SubscriptionRawCSV",
    DatasetResource(
        properties=AzureBlobDataset(
            linked_service_name=LinkedServiceReference(reference_name=linked_service_name),
            folder_path=f"{CONTAINER_NAME}/raw",
            file_name="subscriptions.csv",
            format={"type": "TextFormat", "columnDelimiter": ",", "firstRowAsHeader": True}
        )
    )
)

# Sink dataset (processed CSV)
adf_client.datasets.create_or_update(
    RESOURCE_GROUP,
    ADF_NAME,
    "SubscriptionProcessedCSV",
    DatasetResource(
        properties=AzureBlobDataset(
            linked_service_name=LinkedServiceReference(reference_name=linked_service_name),
            folder_path=f"{CONTAINER_NAME}/processed",
            file_name="subscriptions_processed.csv",
            format={"type": "TextFormat", "columnDelimiter": ",", "firstRowAsHeader": True}
        )
    )
)

# ── Step 5: Create Pipeline ───────────────────────────────────────────────────
print("[5/5] Creating Data Factory Pipeline...")

copy_activity = CopyActivity(
    name="CopySubscriptionCSV",
    inputs=[DatasetReference(reference_name="SubscriptionRawCSV")],
    outputs=[DatasetReference(reference_name="SubscriptionProcessedCSV")],
    source=BlobSource(),
    sink=BlobSink()
)

adf_client.pipelines.create_or_update(
    RESOURCE_GROUP,
    ADF_NAME,
    PIPELINE_NAME,
    PipelineResource(activities=[copy_activity])
)

# Trigger pipeline run
run = adf_client.pipelines.create_run(RESOURCE_GROUP, ADF_NAME, PIPELINE_NAME)
print(f"     Pipeline triggered! Run ID: {run.run_id}")

print("\n=== Setup Complete ===")
print(f"Storage Account : https://portal.azure.com → {STORAGE_ACCOUNT}")
print(f"Data Factory    : https://adf.azure.com")
print(f"Pipeline        : {PIPELINE_NAME}")
print(f"\nNext step: Open notebooks/subscription_analysis.ipynb in Databricks")
