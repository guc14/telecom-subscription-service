# Azure Data Pipeline — Telecom Subscription Analytics

This module extends the Telecom Subscription Service with a cloud data pipeline
that exports subscription data to Azure Blob Storage and runs PySpark analytics
on Azure Databricks.

---

## Architecture

```
MySQL (Telecom DB)
       │
       │  subscriptions.csv (exported)
       ▼
Azure Blob Storage (raw/)
       │
       │  Azure Data Factory Pipeline
       │  CopySubscriptionData
       ▼
Azure Blob Storage (processed/)
       │
       │  Databricks reads via wasbs://
       ▼
Azure Databricks (PySpark)
  - Subscription count by plan
  - Monthly activation trend
  - Churn rate analysis
  - Revenue summary
       │
       ▼
Azure Blob Storage (processed/plan_summary/)
```

---

## Design Decisions

**Why Azure Data Factory (ADF)?**
ADF is the standard ETL orchestration tool in Canadian banks (TD, RBC, BMO all use it).
It provides a no-code pipeline for moving data between sources — here we model a
MySQL → Blob Storage export, which mirrors real bank data warehouse ingestion patterns.

**Why Databricks + PySpark?**
Databricks is the dominant big data analytics platform in financial services.
PySpark allows distributed processing — the same analytics notebook scales from
20 rows (demo) to 20 million rows (production) without code changes.

**Why separate from the Java service?**
- Analytics workloads scale independently from OLTP workloads
- Data scientists can iterate on notebooks without touching production Java code
- Follows the Lambda architecture pattern: Java handles real-time writes,
  Databricks handles batch analytics

---

## Quick Start

### Prerequisites
- Azure account with active subscription
- Python 3.9+
- Azure CLI logged in (`az login`)

### 1. Install dependencies
```bash
cd azure-data-pipeline
pip install -r requirements.txt
```

### 2. Deploy infrastructure and run pipeline
```bash
export AZURE_SUBSCRIPTION_ID=d4dd2968-d53c-497d-b767-e6c95be4326d
python scripts/deploy.py
```

This will:
- Create Azure Storage Account (`telecomstorage001`)
- Upload `subscriptions.csv` to Blob Storage
- Create Azure Data Factory
- Create and trigger the `CopySubscriptionData` pipeline

### 3. Run Databricks analytics
1. Go to [Azure Databricks](https://portal.azure.com) → Create Databricks workspace
2. Create a new Notebook
3. Copy contents of `notebooks/subscription_analysis.py`
4. Set your storage account name and key in Cell 1
5. Run All

---

## Analytics Output

| Metric | Description |
|--------|-------------|
| Subscribers by plan | Count of active/total per plan |
| Monthly revenue | Sum of monthly fees per plan (CAD) |
| Activation trend | New subscriptions per month |
| Churn rate | % of cancelled subscriptions |

---

## Limitations / Future Work

- Currently uses CSV export; production would use ADF's native MySQL connector
- Databricks job scheduling (daily batch) not yet configured
- No data quality checks on ingestion (Great Expectations integration planned)
