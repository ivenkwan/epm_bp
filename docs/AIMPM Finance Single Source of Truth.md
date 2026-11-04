# Finance Single Source of Truth
## Metric Catalogue and Canonical Data Schema

**Version:** 1.0  
**Date:** 4 November 2026  
**Classification:** Confidential — internal financial information  
**Scope:** Management reporting, flash reporting, operations review, scenario analysis and forecasting

## Executive recommendation

Build a **shared finance and operational data model**, rather than three separate report pipelines. Extract source information once at its available grain, standardize it through governed mappings, calculate shared measures centrally, and publish multiple reporting views from the same controlled dataset.

The three supplied reports already share financial measures, business hierarchies and comparison structures, but differ in reporting period, currency, operational detail, estimate maturity and management adjustments. These differences must remain explicit in the common model rather than being flattened into a single undifferentiated “actual” dataset. 

The recommended architecture is:

```mermaid
flowchart LR
    subgraph Sources["Authoritative source systems"]
        ERP["ERP and general ledger"]
        OPS["Sales, service and inventory"]
        PROP["Property and hotel systems"]
        TREAS["Banks, treasury and lending"]
        HR["HR and location registers"]
        PLAN["Budget and forecast submissions"]
    end

    subgraph Foundation["Shared data foundation"]
        RAW["Immutable source landing"]
        STD["Standardization and master-data mapping"]
        DQ["Reconciliation and data-quality controls"]
        FACT["Canonical facts and conformed dimensions"]
        CONS["Versioned consolidation and adjustments"]
        SEM["Governed metrics and semantic layer"]
    end

    subgraph Consumption["Multiple uses"]
        FLASH["Flash report"]
        MR["Monthly management report"]
        REVIEW["Operations review"]
        ADHOC["Ad-hoc analysis"]
        SCEN["Scenario analysis"]
        TS["Time-series forecasting"]
    end

    ERP --> RAW
    OPS --> RAW
    PROP --> RAW
    TREAS --> RAW
    HR --> RAW
    PLAN --> RAW
    RAW --> STD --> DQ --> FACT --> CONS --> SEM
    SEM --> FLASH
    SEM --> MR
    SEM --> REVIEW
    SEM --> ADHOC
    SEM --> SCEN
    SEM --> TS
```

**Important interpretation:** “Consolidate once” means using one reusable, governed consolidation process for each relevant period, scope, basis and version. It does not mean combining daily provisional figures, monthly closed results and later restatements into one permanently fixed number.

***

## 1. Evidence and extraction boundaries

### Source register

Physical PDF page numbers are used throughout this document. This avoids ambiguity where printed page numbers restart or skip.

| Code | Supplied document | Reporting content | Citation |
|---|---|---|---|
| **F** | `A_EPM_Flash-Reports.pdf` | August flash reporting; group estimates, divisional operating profit and PAT, automotive operating detail, machinery and divisional commentary |  |
| **M** | `A_EPM_Group-Current_MR-Reports.pdf` | April 2026 management reporting; financial performance, associates/JVs, financial position, cash flows, capex, facilities, ageing, rental, locations and headcount |   |
| **O** | `A_EPM_Group-Current_Operations-Review-2026.pdf` | First-half 2026 review; June YTD, full-year 6+6 forecast, divisional operations, hotels, ratios, financial position, cash flow, capex and tax |   |

The management-report PDF contains selected sections rather than a continuous reproduction of every page listed in its contents. For example, its printed pagination jumps between sections. Consequently, the catalogue covers the identifiable metrics and variants in the **supplied pages**, not any omitted schedules referenced by those pages.

Several report cells are blank, masked or contain placeholders. Definitions can therefore be reconstructed from labels and notes, but numerical reconciliation and source-system implementation cannot be certified from these PDFs alone. 

### Definition status

| Status | Meaning | Implementation treatment |
|---|---|---|
| **E — Explicit** | A formula or meaning is explicitly stated in a report note | Preserve the source definition and version it |
| **I — Interpreted** | Meaning is inferred from the label or financial structure | Proposed definition; obtain Finance approval |
| **C — Confirm** | Label exists, but scope, denominator or policy is ambiguous | Store source-reported values; do not silently calculate |
| **P — Proposed** | New architectural, analytical or forecasting capability | Not represented as an existing report metric |

**Catalogue convention:** Repeated regional rows are represented by one metric plus dimensions. Revenue in China and revenue in Korea are not different metric definitions; however, statutory PAT, management-adjusted PAT and hotel owner-adjusted PAT are distinct measures.

***

## 2. What should be standardized

### Shared reporting structure

The recurring reporting structure is:

> **Metric × organization × business activity × time × scenario/version × currency × accounting basis × consolidation scope**

Across the reports, the most important additional operational dimensions are brand/model, new/used condition, dealer/agency model, property, hotel, lending portfolio, inventory/receivables ageing and bank facility. 

### Do not standardize away these differences

| Difference | Evidence in supplied reports | Required treatment |
|---|---|---|
| Flash estimate versus later actual | Group flash schedules use estimates, while detailed templates also use “Actual MTD” labels | Preserve both original label and normalized maturity; do not assume all flash cells have identical status.  |
| MTD, YTD and full year | All three packs contain different period presentations | Store period boundaries and aggregation semantics, not only a month label.  |
| Forecast vintages | M refers to 3+9; O presents 6+6 | Separate scenario from forecast version and actual/forecast cutoff.  |
| Local versus presentation currency | Flash automotive pages use local currencies; group schedules use HKD presentation | Retain original currency, scale and conversion basis. |
| Before/after management adjustment | M presents MA adjustments, including MBK-related adjustments | Store adjustments separately with approved accounting and management bases.  |
| Dealer versus agency sales | Automotive volumes and income distinguish sales models | Do not combine gross vehicle turnover and commission-only revenue indiscriminately.  |
| Operational versus financial ownership perimeter | Some automotive units include JVs, while JV profit is separately presented | Separate operational inclusion from financial consolidation.  |
| Bank-specific ratios | Ratio schedules state that banks may use different definitions | Maintain covenant-specific formulas, scope and effective dates.  |
| Gross versus net property yield | O explicitly provides different numerators | Maintain separate metrics.  |

***

## 3. Common dimensions

### Conformed dimension catalogue

The following is the proposed common dimensional model. Report-derived examples are illustrative members, not a complete or legally verified company register.

| Dimension | Key attributes | Report-derived examples or purpose |
|---|---|---|
| **Date** | Calendar date, fiscal period, quarter, half-year, year, business day, timezone | Daily observations; month-end; quarter-end |
| **Reporting period** | Start/end dates, frequency, fiscal calendar, MTD/YTD/FY view | April MTD, June YTD, full-year forecast |
| **Legal entity** | Entity code, legal name, functional currency, jurisdiction | Actual companies must be mapped from ERP/entity master |
| **Management unit** | Division, subgroup, region, operating company, business unit | Automobile, Machinery, Property, Trading, Financial Services, Corporate |
| **Consolidation scope** | Group/subgroup, included entities, method, ownership rules | Group; subsidiaries; associates; JVs; exclusions |
| **Geography** | Country, region, province, city | China, Korea, Taiwan, Vietnam, Australia, Germany, UK and other reported locations |
| **Operating location** | Location code, type, opening/closure dates | Dealership, outlet, workshop, office |
| **Brand** | Brand code, manufacturer, brand family | MB, smart, Porsche, Lamborghini, BYD, Denza, CAT, Perkins, SEM |
| **Product** | Product hierarchy, model, category | Vehicle models; machines; engines; parts; services |
| **Vehicle condition** | New, used; demo flag | New car; used retail including demo |
| **Sales model** | Dealer/principal; agency/commission | Dealer sales versus commission sales |
| **Sales channel** | Retail, wholesale, other approved channels | Used retail versus used wholesale |
| **Powertrain** | ICE, BEV, PHEV, other/unknown | EV/EQ/PHEV analyses |
| **Revenue stream** | Standardized stream and local mapping | Vehicle, commission, parts, labour, rental, hotel, loan interest |
| **Cost category** | Nature, function, controllability, allocation treatment | Personnel, depreciation, selling, MIS, rent |
| **Account** | Local account, group account, statement classification | P&L, balance-sheet and cash-flow mappings |
| **Movement type** | Opening, addition, repayment, FX, impairment, closing | Loan/investment/asset roll-forwards |
| **Counterparty** | Customer, supplier, bank, related-party indicator | OEM, tenant, borrower, bank |
| **Intercompany partner** | Partner entity and matching relationship | Internal rent, interest, sales and balances |
| **Property/space** | Property, phase, building, floor/unit, usage class | Office, retail, industrial, autohaus, land |
| **Lease contract** | Tenant, internal/external, dates, rent terms | Rental income and occupancy analysis |
| **Hotel** | Hotel/property code, room category | Beijing and Xian hotel schedules |
| **Loan/lease portfolio** | Product, geography, borrower, secured status | Term loans, property loans, equipment finance |
| **Bank account** | Bank, entity, currency, restrictions, account type | Cash visibility and reconciliation |
| **Facility** | Bank, borrower, type, limit, maturity, commitment | Loan/bond, RC, OD, dealer financing, LC/others |
| **Ageing policy/bucket** | Age basis, boundaries, exclusive/cumulative flag | Inventory and debtor ageing |
| **Project** | Project, stage, location, capex category, milestone dates | Autohaus, property development, replacement capex |
| **Workforce category** | Function, employee category, outsourced flag | Finance, internal audit, MIS, corporate functions |
| **Scenario** | Actual, budget, forecast, estimate, what-if | Analytical intent |
| **Version** | Vintage, approval status, cutoff, lock date | Original budget; forecast 3+9; forecast 6+6 |
| **Accounting basis** | Statutory, management-adjusted, owner-adjusted | Before/after MA; hotel owner book |
| **Currency basis** | Transaction, functional, presentation, constant currency | Reported HKD versus local-currency performance |
| **Source/publication** | System, file, page, release, extraction batch | End-to-end traceability |

The division, geography, brand, revenue-stream, cost-category, property, portfolio and comparison examples above are drawn from the supplied schedules. 

### Separate hierarchies

Legal ownership, management reporting and operational locations should be related but not treated as the same hierarchy.

```mermaid
flowchart TB
    ENTITY["Legal entity"]
    OWN["Effective-dated ownership bridge"]
    SCOPE["Consolidation scope"]
    MGMT["Management reporting hierarchy"]
    UNIT["Operating unit"]
    LOC["Operating location"]
    GEO["Geography"]
    PRODUCT["Product and brand"]

    ENTITY --> OWN --> SCOPE
    ENTITY --> MGMT
    ENTITY --> UNIT
    UNIT --> LOC
    LOC --> GEO
    UNIT --> PRODUCT

    NOTE["Operational volume inclusion can differ from financial consolidation"]
    SCOPE -.-> NOTE
    UNIT -.-> NOTE
```

**Recommended rules**

- Preserve historical and current organization views separately.
- Effective-date entity-to-division assignments and ownership percentages.
- Treat consolidation adjustments as an **adjustment layer**, not a fictitious operating business.
- Distinguish reporting country, legal domicile and asset location.
- Assign explicit `UNKNOWN` and `NOT_APPLICABLE` members; do not use an ambiguous blank for both.

***

## 4. Metric catalogue conventions

### Metric identity

Each governed metric should have:

```text
metric_code
metric_name
business_definition
formula_expression
definition_status
definition_version
effective_from / effective_to
business_owner
unit_of_measure
value_nature
allowed_dimensions
required_dimensions
aggregation_rule
currency_rule
sign_convention
source_mapping
validation_rules
forecast_eligibility
```

### Aggregation codes

| Code | Meaning |
|---|---|
| **FLOW** | Sum across non-overlapping periods and compatible organizational slices |
| **STOCK** | Sum across compatible entities/items at one as-of date; never sum across dates |
| **RATIO** | Recalculate from aggregated numerator and denominator |
| **UNIT** | Sum qualifying events or distinct items under the approved counting rule |
| **MOVEMENT** | Signed change in a stock; reconcile opening to closing |
| **REPORTED** | Retain published value until calculation is approved |
| **DATE** | Milestone date; not a summable measure |

**Currency-bearing amounts below are stored in base currency units.** Thousands and millions are display scales, not different currencies or metric definitions.

***

## 5. Financial performance metrics

This section normalizes the recurring P&L measures and their visible components. The reports repeatedly present these by division, country/subgroup, scenario and period. 

### Core P&L

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| FIN-001 | Revenue | Recognized revenue mapped to the approved group revenue accounts; preserve gross/net presentation policy | FLOW | I |
| FIN-002 | Cost of sales | Costs assigned to recognized revenue; explicit in hotel schedules and otherwise needed to reconcile GP | FLOW | I |
| FIN-003 | Gross profit / loss | Revenue less cost of sales, including only approved gross-profit adjustments | FLOW | I |
| FIN-004 | Operating expenses | Operating costs outside cost of sales; components governed by account mapping | FLOW | I |
| FIN-005 | Other income / expenses, net | Approved non-revenue operating income less associated expenses | FLOW | I |
| FIN-006 | Operating profit / loss | GP plus net other operating income less operating expenses, subject to report-specific classifications | FLOW | I |
| FIN-007 | Finance costs | Reported finance expense; keep capitalized interest separately identifiable | FLOW | I |
| FIN-008 | Profit / loss before tax | Approved pre-tax result for the specified perimeter; equity-accounted results must follow the relevant report basis | FLOW | C |
| FIN-009 | Tax charge / credit | Total income-tax-related expense or credit under the approved tax mapping | FLOW | I |
| FIN-010 | Subsidiaries PAT | Consolidated post-tax result of subsidiaries for the relevant scope | FLOW | I |
| FIN-011 | Share of associates’ PAT | Recognized group share of associate results, after applicable adjustments | FLOW | C |
| FIN-012 | Share of JVs’ PAT | Recognized group share of JV results, after applicable adjustments | FLOW | C |
| FIN-013 | Group PAT | Subsidiaries PAT plus associate/JV contributions and applicable group adjustments, without double counting | FLOW | I |
| FIN-014 | Non-recurring / one-off items | Approved separately identified items used in the adjusted-PAT bridge | FLOW | C |
| FIN-015 | Adjusted group PAT | Group PAT excluding signed one-off contributions under an approved adjustment policy | FLOW | C |
| FIN-016 | Net operating expenses / income | Net operating-cost presentation used in divisional schedules | FLOW | C |
| FIN-017 | Management adjustment | Separately recorded adjustment bridging published financial bases | FLOW | C |
| FIN-018 | Consolidation adjustment | Elimination, reclassification or other consolidation journal contribution | FLOW | I |

The adjusted-group-PAT presentation is visible in O; MA-adjusted presentations are visible in M. 

### Revenue and income components

These are **members of `revenue_stream` or `income_category`**, not independent, incompatible revenue schemas.

| Code | Reported component | Definition / scope | Status |
|---|---|---|---|
| REV-001 | Vehicle sales | Recognized principal/dealer vehicle sales revenue | I |
| REV-002 | Car / vehicle sales commission income | Agency/commission income; not gross vehicle transaction value | I |
| REV-003 | Aftersales | Workshop/parts/service revenue under the local-to-group mapping | C |
| REV-004 | Workshop parts, including OTC | Parts revenue; OTC means the report-labelled over-the-counter component | I |
| REV-005 | Workshop services / labour | Labour and other workshop-service revenue | I |
| REV-006 | Aftermarket | Revenue from approved aftermarket activities | C |
| REV-007 | Car rental | Rental revenue from vehicle-rental activities | I |
| REV-008 | Extended warranty | Recognized extended-warranty revenue | C |
| REV-009 | Property rental income | Rental income; retain gross/net and intercompany classifications | C |
| REV-010 | Hotel income | Room, F&B and other hotel revenue | I |
| REV-011 | Finance income | Reported automotive or other finance-income stream | C |
| REV-012 | Financing commission | Financing-related commission income | I |
| REV-013 | Insurance commission | Insurance-related commission income | I |
| REV-014 | F&I commission | Combined financing and insurance commission; do not add again to its components | I |
| REV-015 | Machinery sales | Machine-related recognized revenue | I |
| REV-016 | Energy and transportation | Engine/power-system and related revenue | I |
| REV-017 | Product support | Machinery support, parts and service revenue under approved mapping | C |
| REV-018 | CV revenue | Report-labelled CV revenue; acronym and business scope require confirmation | C |
| REV-019 | Term-loan income | Recognized income from term-loan business | C |
| REV-020 | Property-loan income | Recognized income from property-loan business | C |
| REV-021 | Penalty fee income | Recognized penalty fees | I |
| REV-022 | Setup and renewal fee income | Recognized loan setup/renewal fees; accounting timing must be specified | C |
| REV-023 | Realized proprietary share-trading gain | Realized gain/loss from proprietary securities trading | I |
| REV-024 | Dividend income | Recognized dividend income | I |
| REV-025 | Bank interest income | Interest earned on bank balances/deposits | I |
| REV-026 | Intercompany interest income | Interest from related group entities, before elimination | I |
| REV-027 | Safety-net income | Separately labelled income in Korea MB review; classification requires approval | C |
| REV-028 | Other income / other revenue | Residual approved categories; not a substitute for detailed mapping | C |

Automotive revenue and commission components appear in F and M; machinery and lending components appear in M; hotel and safety-net income are additionally identifiable in O. 

### Gross-profit and operating components

| Code | Metric / component | Definition / calculation | Type | Status |
|---|---|---|---|---|
| GP-001 | Vehicle GP before SI/VM | Vehicle GP excluding sales incentives and variable margin | FLOW | I |
| GP-002 | Sales incentives — SI | Recognized manufacturer/OEM sales-incentive contribution | FLOW | C |
| GP-003 | Vehicle GP including SI, excluding VM | GP-001 plus GP-002, only where local presentation confirms this bridge | FLOW | C |
| GP-004 | Variable margin — VM | Recognized variable-margin contribution | FLOW | C |
| GP-005 | Vehicle GP including SI/VM | GP before SI/VM plus approved SI and VM components | FLOW | C |
| GP-006 | Parts VM | Parts variable-margin contribution; distinguish embedded versus separately reported | FLOW | C |
| GP-007 | Sales warranty contribution | Signed warranty-related contribution in the GP section | FLOW | C |
| GP-008 | CAT support income | CAT support contribution reported in machinery GP | FLOW | C |
| GP-009 | Provision write-back on sales-related expenses | Signed reversal reported in machinery operating analysis | FLOW | C |
| GP-010 | GP1 | Market-specific GP tier; preserve reported value until its component bridge is approved | REPORTED | C |
| GP-011 | GP3 | Market-specific GP tier; do not assume equivalence to GP1 or total GP | REPORTED | C |
| OI-001 | Net exchange gain / loss | Signed recognized FX result; retain realized/unrealized and P&L/translation distinctions | FLOW | C |
| OI-002 | Fair-value gain / loss on investment properties | Signed valuation movement recognized under the approved accounting treatment | FLOW | I |
| OI-003 | Mark-to-market adjustment | Signed financial-services valuation adjustment | FLOW | C |
| OI-004 | Bad-debt / impairment expense or reversal | Period expense/reversal; distinct from closing allowance balance | FLOW | I |

SI, VM, GP1, GP3, warranty and support-income variants occur across the automotive and machinery schedules. Property fair-value and financial-services valuation treatments are separately visible. 

### Operating-expense components

Each row is a standardized `cost_category`; aggregate through FIN-004 only once.

| Code | Reported category | Definition | Status |
|---|---|---|---|
| EXP-001 | Advertising and marketing | Advertising, promotion and marketing costs | I |
| EXP-002 | Selling and distribution | Selling/distribution functional costs; may contain nature-based costs elsewhere | C |
| EXP-003 | Personnel costs | Employee-related operating expense under approved inclusion rules | C |
| EXP-004 | Travelling and entertainment | Business travel and entertainment expense | I |
| EXP-005 | Depreciation — PPE | Period depreciation of owned PPE | I |
| EXP-006 | Depreciation — ROU | Period depreciation of right-of-use assets | I |
| EXP-007 | Amortization — intangibles | Period amortization of intangible assets | I |
| EXP-008 | Land amortization | Report-labelled amortization of land-related balances | C |
| EXP-009 | Rental expenses | Recognized rent-related expense under the applicable accounting basis | C |
| EXP-010 | MIS expenses | Management-information/IT expenses under approved scope | C |
| EXP-011 | Miscellaneous tax | Operating taxes outside the income-tax line | C |
| EXP-012 | Administrative and general | Administrative/general functional expense | C |
| EXP-013 | Other operating expenses | Approved remaining operating expenses | C |
| EXP-014 | Intercompany consultancy fee | Internal consultancy charge before elimination | I |
| EXP-015 | Hotel-building depreciation | Hotel-building depreciation separately identified in owner-book adjustments | I |

The reports use both expense-by-function and expense-by-nature presentations. They must be mapped as alternative classification axes, not summed as if all categories were mutually exclusive. 

***

## 6. Ratios and unit economics

All ratios should be calculated from the same governed amount and volume measures used by the reports.

| Code | Metric | Proposed definition / treatment | Status |
|---|---|---|---|
| RAT-001 | Gross margin | GP / revenue for the same scope | I |
| RAT-002 | Operating margin | Operating profit / revenue | I |
| RAT-003 | Opex / revenue | Operating expense magnitude / revenue | I |
| RAT-004 | Opex / GP | Operating expense magnitude / GP | I |
| RAT-005 | PBT ROS | PBT / revenue; preserve the approved PBT perimeter | C |
| RAT-006 | PAT margin / PAT ROS | PAT / revenue for the specified entity/scope | I |
| RAT-007 | Adjusted PAT margin | Adjusted group PAT / revenue | I |
| RAT-008 | Effective tax rate | Tax expense / approved pre-tax denominator; flag loss/near-zero denominators | C |
| RAT-009 | Revenue per vehicle | Matching vehicle revenue / qualifying vehicle units | I |
| RAT-010 | GP per vehicle | Matching GP tier / qualifying vehicle units | C |
| RAT-011 | Commission per vehicle | Commission income / matching commissioned units | C |
| RAT-012 | Commission as % of car sales | Commission income / approved car-sales revenue denominator | C |
| RAT-013 | Used/new car ratio | Used-car units / new-car units for the same scope | C |
| RAT-014 | Car sales mix | Units of qualifying category / total units in the explicitly defined population | C |
| RAT-015 | SI as % of car revenue | SI / matching car revenue | I |
| RAT-016 | VM as % of car revenue | VM / matching car revenue | I |
| RAT-017 | Workshop revenue per throughput | Workshop revenue / service-throughput units | I |
| RAT-018 | Parts revenue per throughput | Workshop-parts revenue / approved throughput population | C |
| RAT-019 | Labour revenue per throughput | Workshop-labour revenue / approved throughput population | C |
| RAT-020 | Workshop GP margin | Workshop GP / matching workshop revenue | I |
| RAT-021 | Workshop absorption rate | Reported absorption measure; eligible GP and overhead denominator must be confirmed | C |
| RAT-022 | Financial-services ROS excluding MTM | Approved operating result excluding MTM / approved revenue denominator | C |
| RAT-023 | Associate contribution / net revenue | Reported contribution amount / net revenue | C |
| RAT-024 | Associate marketing cost / net revenue | Marketing cost / net revenue | I |
| RAT-025 | Associate G&A / net revenue | G&A expense / net revenue | I |
| RAT-026 | Associate return on sales before interest and tax | Approved EBIT-equivalent / net revenue | C |

The ratio labels, per-unit analyses, workshop absorption, associate margins and financial-services ROS variants appear in the supplied packs. Most denominators are not fully specified and therefore require approval rather than assumption. 

**Calculation policy**

- Return `NULL` plus a reason code for a zero or invalid denominator.
- Do not average regional margins to obtain a group margin.
- Do not sum monthly ratios to obtain YTD ratios.
- Keep percentage-point change separate from percentage growth.
- A percentage with a negative comparator requires an approved display policy.

***

## 7. Automotive and machinery volumes

### Volume measures

| Code | Metric | Definition / counting rule | Type | Status |
|---|---|---|---|---|
| VOL-001 | Dealer vehicle sales units | Qualifying delivered/sold vehicle events under the dealer model; recognition event must be agreed | UNIT | C |
| VOL-002 | Commission / agency vehicle units | Qualifying agency transactions, separate from dealer units | UNIT | C |
| VOL-003 | Total vehicle sales units | Non-overlapping dealer plus agency units within the selected operational perimeter | UNIT | I |
| VOL-004 | New-car units | VOL-001/002 filtered to new condition | UNIT | I |
| VOL-005 | Used-car retail units | Used vehicles sold through retail; preserve demo flag | UNIT | I |
| VOL-006 | Used-car wholesale units | Used vehicles sold through wholesale channel | UNIT | I |
| VOL-007 | Service throughput | Reported workshop service-unit count; job, visit or vehicle counting convention requires confirmation | UNIT | C |
| VOL-008 | Machinery sales units | Qualifying machinery sale events, excluding other product groups | UNIT | C |
| VOL-009 | Engine / E&T sales units | Qualifying engine/E&T unit sales | UNIT | C |
| VOL-010 | Wholesale delivery units | Associate-reported wholesale deliveries | UNIT | C |
| VOL-011 | Portfolio equipment units | Units underlying finance-lease/equipment portfolio schedules | STOCK | C |
| VOL-012 | Rental-category machinery units | Units reported under machine-rental categories; distinguish contracts, fleet and disposals | REPORTED | C |

These volumes are reported at different levels and with different inclusion rules, including JV-inclusive automotive totals. 

### Product and category members

| Axis | Members identifiable in the supplied reports |
|---|---|
| Vehicle class | Passenger car; commercial vehicle/van |
| Brand | MB; smart; Porsche; Lamborghini; BYD; Denza; other/non-MB |
| Vehicle model/family | A, C, E, S, CLA, CLE, GLA, GLB, GLC, GLE, GLS, G-Class, V-Class, AMG, Maybach, EQ/EQA/EQB/EQE/EQS and reported SUV/BEV variants |
| Automotive sourcing/mix | PbP; CBU; AMG and other groupings |
| Machinery | CAT machines; used equipment; SEM; allied products; rental |
| Engine/power system | CAT EP, industrial, petroleum, marine, electrification, solar turbines, microgrid; Perkins; Wilson; other engines |
| Machinery support | Parts; WIP; product support; business development |
| Equipment-finance portfolio | Prime product; SEM; used equipment; rent-transfer/rental-to-rental; engines; aerial equipment; precision engineering; automobile/properties; others |

These are dimensional members observed in the source schedules; acronym expansions and overlapping category boundaries require business validation. 

**Critical controls**

- EV/EQ/PHEV may be a subset of a brand/class total. Do not add it to that total.
- “Total including JVs” and “consolidated subsidiary revenue” can legitimately have different perimeters.
- PbP/CBU, model, brand and powertrain should not be forced into one mutually exclusive hierarchy.
- Revenue per unit must match principal/agency model and new/used population.

***

## 8. Property and hotel metrics

### Property and rental

M provides property-level leaseable area, leased area, occupancy, rental, rent per sqm, property cost and gross yield. O explicitly defines gross and net rental yields and separately presents investment-property fair-value movements. 

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| PRP-001 | Leaseable area | Area available for leasing under the approved property/space perimeter | STOCK, sqm | I |
| PRP-002 | Leased area | Area covered by qualifying leases at the snapshot date | STOCK, sqm | C |
| PRP-003 | Occupancy rate | Leased area / leaseable area, using identical area definitions | RATIO | I |
| PRP-004 | Monthly rental | Reported monthly rental amount; distinguish contractual run-rate from recognized monthly income | FLOW/REPORTED | C |
| PRP-005 | Annual rental | Annual rental amount used by the schedule; distinguish annualized run-rate from actual FY income | FLOW/REPORTED | C |
| PRP-006 | Monthly rent per sqm | Monthly rental / approved area denominator | RATIO | C |
| PRP-007 | Property cost | Cost basis used for rental-yield calculations | STOCK | C |
| PRP-008 | Gross rental yield | Annual rental income / property cost | RATIO | E |
| PRP-009 | Net rental yield | Annual PAT after property depreciation and land amortization / property cost | RATIO | E |
| PRP-010 | Period investment-property FV movement | Fair-value gain/loss for the stated reporting period | FLOW | I |
| PRP-011 | FV movement since acquisition | Cumulative fair-value movement since acquisition, not a period flow | STOCK/REPORTED | I |
| PRP-012 | Internal rental contribution | Rental income/expense between group entities before elimination | FLOW | I |
| PRP-013 | External rental contribution | Rental income from third parties | FLOW | I |

**Yield controls**

- Preserve the Korea property exclusion flagged in M rather than including every property automatically.
- Internal and external tenants must be distinguishable.
- Property cost is not automatically identical to current fair value.
- A full-year yield must not be generated by summing monthly yields. 

O notes that no budget/forecast is available for certain fair-value movements because assessments are conducted annually. Missing forecast values must therefore remain unavailable unless Finance explicitly introduces a scenario assumption. 

### Hotel operations and owner adjustments

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| HTL-001 | Rooms available | Proposed canonical basis: available room-nights over the period; validate against the report label | UNIT | C |
| HTL-002 | Rooms sold | Proposed canonical basis: occupied/sold room-nights over the period | UNIT | C |
| HTL-003 | Hotel occupancy | Rooms sold / rooms available | RATIO | I |
| HTL-004 | Average room rate | Room revenue / sold room-nights; confirm tax/service-charge treatment | RATIO | C |
| HTL-005 | RevPAR | Room revenue / available room-nights | RATIO | I |
| HTL-006 | Room revenue | Recognized room income | FLOW | I |
| HTL-007 | F&B revenue | Recognized food-and-beverage income | FLOW | I |
| HTL-008 | Other hotel revenue | Recognized hotel income excluding room and F&B | FLOW | I |
| HTL-009 | Total hotel income | Room + F&B + other hotel revenue | FLOW | I |
| HTL-010 | Owner-book adjustment total | Signed total of approved owner adjustments | FLOW | C |
| HTL-011 | Owner PAT / loss | Hotel PAT plus signed owner-book adjustments | FLOW | C |
| HTL-012 | Cost of share capital | Reported owner adjustment; calculation and whether accounting or analytical must be confirmed | REPORTED | C |
| HTL-013 | Adjusted owner PAT / loss | Owner PAT plus signed cost-of-share-capital adjustment under approved policy | FLOW | C |
| HTL-014 | Hotel headcount | Staff count at the specified snapshot date | STOCK | C |

Owner adjustments shown include administrative/general costs, hotel-building depreciation, intercompany interest and, where applicable, intercompany consultancy fees. 

***

## 9. Financial position metrics

M provides a detailed statement of financial position and supporting notes; O repeats a summarized balance sheet and selected supporting schedules. 

### Balance-sheet measures

All amounts below are **STOCK** measures. Current/non-current is a dimension or account classification, not a separate incompatible metric.

| Code | Balance / metric | Definition |
|---|---|---|
| BS-001 | Property, plant and equipment | Closing carrying amount of PPE |
| BS-002 | Hotel property | Closing carrying amount of hotel-property assets |
| BS-003 | Investment properties | Closing carrying amount of investment properties |
| BS-004 | Right-of-use assets | Closing ROU carrying amount |
| BS-005 | Properties under development | Closing carrying amount of development projects |
| BS-006 | Goodwill | Closing goodwill balance |
| BS-007 | Intangible assets | Closing intangible-asset carrying amount |
| BS-008 | Investments in JVs | Closing investment carrying amount |
| BS-009 | Investments in associates | Closing investment carrying amount |
| BS-010 | Deferred-tax assets | Closing recognized deferred-tax asset |
| BS-011 | Net investment in finance leases | Finance-lease receivable/investment balance under approved accounting mapping |
| BS-012 | Loan receivables | Closing loan receivable balance; gross/net and interest inclusion must be explicit |
| BS-013 | Prepayments, deposits and other receivables | Closing balance of approved mapped accounts |
| BS-014 | Other non-current assets | Closing residual non-current assets |
| BS-015 | Inventories | Closing gross or net inventory, explicitly identified |
| BS-016 | Properties held for sale | Closing carrying amount of sale properties |
| BS-017 | Trade, bills and margin-client receivables | Closing balance under the report’s grouping |
| BS-018 | Financial assets at FVTPL | Closing fair value of qualifying financial assets |
| BS-019 | Cash and cash equivalents — balance sheet | Closing amount under the balance-sheet policy |
| BS-020 | Bank balances held for customers | Closing client-money balance, separate from freely available group liquidity |
| BS-021 | Trade and bills payables | Closing trade/bills payable balance |
| BS-022 | Other payables, accruals and other liabilities | Closing balance of approved mapped liabilities |
| BS-023 | Interest-bearing bank and other borrowings | Closing borrowing carrying amount |
| BS-024 | Lease liabilities | Closing lease liability |
| BS-025 | Tax payable | Closing current tax liability |
| BS-026 | Product-warranty provision | Closing warranty liability/provision |
| BS-027 | Deferred-tax liabilities | Closing recognized deferred-tax liability |
| BS-028 | Share capital | Closing issued-share-capital balance |
| BS-029 | Reserves attributable to owners | Closing approved reserve balances, excluding NCI where specified |
| BS-030 | Non-controlling interests | Closing NCI component |
| BS-031 | Total non-current assets | Sum of approved non-current asset accounts |
| BS-032 | Total current assets | Sum of approved current asset accounts |
| BS-033 | Total current liabilities | Sum of approved current liabilities |
| BS-034 | Net current assets | Current assets minus current liabilities |
| BS-035 | Total assets less current liabilities | Total assets minus current liabilities |
| BS-036 | Total non-current liabilities | Sum of approved non-current liabilities |
| BS-037 | Net assets | Total assets minus total liabilities |
| BS-038 | Capital and reserves | Owners’ share capital plus applicable reserves |
| BS-039 | Total equity | Owners’ equity plus NCI |

**Definition status:** I for general meaning; C for exact account membership, gross/net presentation and accounting policy. These definitions are not substitutes for a signed-off chart-of-accounts mapping.

### Supporting asset and reserve components

| Code | Component | Canonical treatment | Status |
|---|---|---|---|
| BSC-001 | Hotel land cost | Asset-cost component by hotel | I |
| BSC-002 | Hotel development cost | Asset-cost component by hotel | I |
| BSC-003 | Accumulated depreciation | Contra-asset balance, not period depreciation expense | I |
| BSC-004 | Prepaid land premiums | ROU/land-related subcategory under approved mapping | C |
| BSC-005 | Tenancy commitments — report ROU category | Preserve as the report’s asset category; do not reinterpret as an off-balance commitment without confirmation | C |
| BSC-006 | Leasehold land | ROU/land-related subcategory | C |
| BSC-007 | Auto deposits paid | Receivables/prepayments component | I |
| BSC-008 | SI and VM rebate receivables | Receivable balance; distinct from incentive income recognized in the period | I |
| BSC-009 | Customer deposits received | Liability component | I |
| BSC-010 | Contract liabilities | Deferred revenue/contract liability component | I |
| BSC-011 | Land/building revaluation reserve | Equity reserve category | I |
| BSC-012 | Reserve funds | Equity reserve category | C |
| BSC-013 | Fair-value reserve | Equity reserve category | C |
| BSC-014 | Exchange-fluctuation reserve | Equity translation reserve, by currency/entity where available | I |
| BSC-015 | Retained profits | Accumulated retained earnings including relevant current-year movements | I |
| BSC-016 | MA adjustment to reserves | Management-basis adjustment, separately posted | C |

These components are visible in M’s asset, receivable, liability and reserve notes. 

***

## 10. Portfolio and investment movements

### Loans and finance leases

M distinguishes lending balances, portfolio movements, allowance deductions and portfolio composition. The Singapore portfolio has a different displayed bridge from other lending portfolios, so one universal formula should not be imposed without mapping approval. 

| Code | Metric | Definition / treatment | Type | Status |
|---|---|---|---|---|
| LON-001 | Opening portfolio balance | Prior-period closing balance on the same basis | STOCK | I |
| LON-002 | New loan drawdown | Principal advanced during the period | MOVEMENT | I |
| LON-003 | Finance-lease new acquisitions | New portfolio additions under the lease schedule’s definition | MOVEMENT | C |
| LON-004 | Instalment repayments | Principal/approved portfolio reductions from scheduled repayments | MOVEMENT | C |
| LON-005 | Prepayments | Early principal repayments | MOVEMENT | I |
| LON-006 | Repossessions | Signed portfolio movement associated with repossessed assets | MOVEMENT | C |
| LON-007 | FX revaluation / realignment | Currency-related portfolio movement | MOVEMENT | C |
| LON-008 | Interest and other charges receivable | Receivable balance or movement as identified by the source bridge | STOCK/MOVEMENT | C |
| LON-009 | Gross closing portfolio | Closing exposure before specified deductions | STOCK | C |
| LON-010 | Bad-debt provision | Closing allowance balance | STOCK | I |
| LON-011 | Deferred interest income | Unearned/deferred-interest deduction | STOCK | C |
| LON-012 | Unearned overdue interest | Separately reported deduction | STOCK | C |
| LON-013 | Management-fee amortization / adjustment | Report-labelled portfolio adjustment; balance versus movement needs confirmation | REPORTED | C |
| LON-014 | Internal properties/machinery deduction | Internal portfolio component excluded in the reconciliation | STOCK | C |
| LON-015 | Net closing portfolio | Gross closing balance less approved deductions | STOCK | C |
| LON-016 | Provision coverage ratio | Closing allowance / approved exposure denominator | RATIO | C |
| LON-017 | Interest receivable excluded from principal | Separately stored accrued-interest balance | STOCK | I |
| LON-018 | Portfolio units by equipment category | Closing qualifying financed units | STOCK | C |

### Associates, JVs and investment carrying values

| Code | Metric | Definition / treatment | Type | Status |
|---|---|---|---|---|
| INV-001 | Investee PAT | Investee’s total post-tax result before ownership multiplication | FLOW | I |
| INV-002 | Ownership / profit-share percentage | Effective share applicable to that entity and period | RATIO | C |
| INV-003 | Group share of investee PAT | Approved investee result × applicable share, plus/minus approved adjustments | FLOW | C |
| INV-004 | Unrealized stock-profit adjustment | Group adjustment for unrealized profit in stock | FLOW | C |
| INV-005 | Opening investment carrying value | Prior closing investment balance | STOCK | I |
| INV-006 | Share-of-profit movement | Carrying-value movement from recognized profit share | MOVEMENT | I |
| INV-007 | FX and other investment movements | Other movements, with FX separately coded where possible | MOVEMENT | C |
| INV-008 | Closing investment carrying value | Opening plus approved signed movements | STOCK | I |
| INV-009 | Goodwill FX/other movement | Movement in goodwill due to FX and other approved changes | MOVEMENT | C |
| INV-010 | Associate contribution amount | Investee-reported contribution measure; not automatically identical to group GP | REPORTED | C |
| INV-011 | Associate marketing cost | Investee marketing expense | FLOW | I |
| INV-012 | Associate G&A expense | Investee administrative expense | FLOW | I |
| INV-013 | Associate net interest | Investee interest income/expense | FLOW | I |
| INV-014 | Associate tax | Investee tax charge/credit | FLOW | I |
| INV-015 | Associate net profit | Investee net result in the supporting schedule | FLOW | I |
| INV-016 | Management-capped associate result | Result under the specific ROS/capping policy in the flash/MA presentation | REPORTED | C |

M explicitly distinguishes associate statements received near month-end from PAT figures supplied for flash reporting. Those should be separate source releases of the same investee-period, not two additive profit contributions. 

F refers to capped/ROS-based associate treatment, but the supplied values do not establish a complete executable rule. Thresholds and calculation details must be obtained from Finance. 

***

## 11. Cash-flow metrics

M and O provide cash-flow totals and explanatory bridges. The following common measures preserve those distinctions. 

### Cash-flow statement

| Code | Metric | Definition | Type | Status |
|---|---|---|---|---|
| CF-001 | Operating cash flow | Net classified operating cash movement | FLOW | I |
| CF-002 | Investing cash flow | Net classified investing cash movement | FLOW | I |
| CF-003 | Financing cash flow | Net classified financing cash movement | FLOW | I |
| CF-004 | Net change in cash equivalents | CF-001 + CF-002 + CF-003, before separately presented FX effects | FLOW | I |
| CF-005 | Opening cash equivalents | Opening balance under the cash-flow policy | STOCK | I |
| CF-006 | FX effect on cash | Translation effect reconciling opening and closing cash | MOVEMENT | I |
| CF-007 | Closing cash equivalents — cash flow | Opening + cash movements + FX effect | STOCK | I |
| CF-008 | Cash and bank balances | Underlying cash/bank component | STOCK | I |
| CF-009 | Time deposits | Deposit component classified by original maturity/restriction | STOCK | C |
| CF-010 | Restricted cash | Restricted component with restriction classification | STOCK | C |
| CF-011 | Deposits over three months at acquisition | Deposit component excluded where the cash-flow policy requires | STOCK | C |
| CF-012 | Bank overdrafts in cash-equivalent bridge | Overdraft component included/excluded according to policy | STOCK | C |
| CF-013 | Adjusted operating profit for cash bridge | Report-specific pre-working-capital cash-flow bridge total | FLOW | C |

### Cash-flow bridge components

These are `cash_flow_component` or `movement_type` members, not additional cash balances.

| Code | Component | Treatment |
|---|---|---|
| CFB-001 | Finance-cost add-back | Non-operating/accrual adjustment as defined by the cash-flow bridge |
| CFB-002 | PPE depreciation add-back | Non-cash period depreciation |
| CFB-003 | ROU depreciation add-back | Non-cash ROU depreciation |
| CFB-004 | Intangible amortization add-back | Non-cash period amortization |
| CFB-005 | Warranty provision adjustment | Signed non-cash/provision adjustment |
| CFB-006 | Associate/JV result adjustment | Reverse non-operating equity-accounted result where applicable |
| CFB-007 | Inventory write-down | Non-cash write-down to net realizable value |
| CFB-008 | Loss/gain on PPE disposal | Adjustment separating accounting disposal result from proceeds |
| CFB-009 | Interest-income adjustment | Adjustment under the approved cash-flow classification |
| CFB-010 | Change in prepayments/deposits/other receivables | Operating movement after excluding FX and non-cash/reclassification effects |
| CFB-011 | Change in loans and receivables | Approved cash-flow movement |
| CFB-012 | Change in trade/bills/client receivables | Approved cash-flow movement |
| CFB-013 | Change in inventories | Approved cash-flow movement |
| CFB-014 | Change in properties held for sale | Approved cash-flow movement |
| CFB-015 | Change in client bank balances | Approved client-money cash-flow movement |
| CFB-016 | Tax paid | Cash income-tax payment |
| CFB-017 | Change in other payables/accruals | Approved cash-flow movement |
| CFB-018 | ROU-related cash-flow adjustment | Preserve source bridge; distinguish asset recognition from actual cash payment |
| CFB-019 | Change in warranty provision | Approved provision/cash settlement bridge movement |
| CFB-020 | Change in finance-lease investment | Approved financing/operating portfolio cash-flow movement |
| CFB-021 | Capital expenditure, including property projects | Cash expenditure classified to investing |
| CFB-022 | PPE disposal proceeds | Cash received on disposals |
| CFB-023 | Investment-property disposal proceeds | Cash received on property disposals |
| CFB-024 | Bank interest received | Cash interest receipt |
| CFB-025 | Net loan repayment / borrowing | Financing cash movement |
| CFB-026 | Lease payments | Cash lease payment, split where required |
| CFB-027 | Interest paid | Cash interest payment |

The components above are visible in the cash-flow commentary/bridge; exact signs and classifications must follow the approved accounting policy. 

**Important:** Do not derive cash flow by simply subtracting balance sheets. Remove FX, acquisitions/disposals, non-cash movements and reclassifications first.

***

## 12. Treasury and capital metrics

### Bank facilities

M’s bank-facility schedule is explicitly extracted from a daily cash-flow report and presents limits, utilization and available balances by bank and region. 

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| TRY-001 | Facility limit | Approved available contractual limit at the snapshot date | STOCK | C |
| TRY-002 | Utilized loan / bond | Drawn exposure classified as loan/bond | STOCK | I |
| TRY-003 | Utilized RC | Exposure under report-labelled RC category; confirm acronym and instrument mapping | STOCK | C |
| TRY-004 | Utilized OD | Overdraft exposure | STOCK | I |
| TRY-005 | Utilized dealer financing | Dealer-financing exposure | STOCK | I |
| TRY-006 | Utilized LC / others | LC and other utilization, retaining subcategories | STOCK | C |
| TRY-007 | Total facility utilization | Non-overlapping utilization components | STOCK | I |
| TRY-008 | Available / unutilized facility | Limit less utilization, adjusted for shared-limit constraints and availability rules | STOCK | C |
| TRY-009 | Utilization percentage | Utilization / approved available limit | RATIO | I |
| TRY-010 | Bank share of total limits | Bank limit / group limit | RATIO | C |
| TRY-011 | Bank share of utilized exposure | Bank utilization / total utilization | RATIO | C |
| TRY-012 | Available percentage | Available amount / approved limit | RATIO | C |
| TRY-013 | Bank guarantee contingent liabilities | Guarantees included in relevant debt/covenant definitions | STOCK | C |
| TRY-014 | Short-term borrowings net of front-end fees | Borrowings carrying amount under the ratio schedule | STOCK | I |
| TRY-015 | Long-term borrowings net of front-end fees | Borrowings carrying amount under the ratio schedule | STOCK | I |
| TRY-016 | Amount due to/from holding company | Signed related-party funding balance included where specified | STOCK | C |

**Control:** Shared parent limits and sublimits require a facility hierarchy. Summing every limit row can overstate available liquidity.

### Liquidity and gearing

The reports explicitly define gearing using net debt including specified contingent liabilities over tangible net worth excluding NCI, and provide an interest-cover bridge. M and O also warn that individual bank definitions may differ. 

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| CAP-001 | Current ratio | Current assets / current liabilities; verify report-specific exclusions | RATIO | I |
| CAP-002 | Covenant total debt | Approved borrowings + included guarantees/other funding − eligible cash | STOCK | E/C |
| CAP-003 | Tangible net worth | Share capital + qualifying reserves excluding NCI − goodwill − intangible assets, under the stated policy | STOCK | E/C |
| CAP-004 | Gearing ratio | CAP-002 / CAP-003 | RATIO | E |
| CAP-005 | Gearing excluding Property | Same approved formula applied to the specified exclusion perimeter | RATIO | C |
| CAP-006 | Gearing excluding credit financing | Same approved formula with approved financing exclusions | RATIO | C |
| CAP-007 | Capitalized interest | Period interest capitalized rather than expensed | FLOW | I |
| CAP-008 | Net finance cost for interest cover | Finance cost + capitalized interest − bank interest income | FLOW | E |
| CAP-009 | Adjusted EBIT component | Operating profit − bank interest income + share of associates’ and JVs’ PBT | FLOW | E |
| CAP-010 | Interest-cover earnings numerator | CAP-009 + approved depreciation/amortization | FLOW | E/C |
| CAP-011 | Interest cover | CAP-010 / CAP-008 | RATIO | E |
| CAP-012 | YTD exchange-reserve movement | Closing exchange reserve − opening reserve, adjusted to the approved movement basis | MOVEMENT | C |

M labels its interest-cover numerator “Adjusted EBITA” while its explanatory note refers to EBITDA; O labels it “Adjusted EBITDA.” Preserve the actual component formula and resolve the naming discrepancy before publishing a single certified metric. 

***

## 13. Inventory and debtor metrics

### Inventory

M’s stock-ageing schedules include units, gross stocks, provision, net stocks, ageing categories and turnover days. Some categories explicitly lack unit or ageing information. 

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| STK-001 | Inventory units | Qualifying on-hand units at the snapshot date | STOCK | C |
| STK-002 | Gross inventory value | Inventory value before allowance/write-down | STOCK | I |
| STK-003 | Inventory provision | Closing inventory allowance | STOCK | I |
| STK-004 | Net inventory value | Gross value less allowance under normalized signs | STOCK | I |
| STK-005 | Current-age inventory | Value in the report’s current-age category | STOCK | C |
| STK-006 | Inventory over six months | Value satisfying the source threshold | STOCK | C |
| STK-007 | Inventory over twelve months | Value satisfying the source threshold | STOCK | C |
| STK-008 | Inventory turnover days | Reported inventory-days metric; numerator basis and cost/sales denominator require confirmation | REPORTED | C |
| STK-009 | Inventory FA adjustment | Financial-accounting adjustment to operational stock | STOCK/MOVEMENT | C |
| STK-010 | JV inventory adjustment | Adjustment aligning operational and financial inventory perimeters | STOCK/MOVEMENT | C |

### Debtors

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| AR-001 | Gross debtors | Receivables before allowances | STOCK | I |
| AR-002 | Current debtors | Not-overdue or report-defined current receivables | STOCK | C |
| AR-003 | Over 30 days | Receivables satisfying the source ageing rule | STOCK | C |
| AR-004 | Over 60 days | Receivables satisfying the source ageing rule | STOCK | C |
| AR-005 | Over 90 days | Receivables satisfying the source ageing rule | STOCK | C |
| AR-006 | Over one year | Receivables satisfying the source ageing rule | STOCK | C |
| AR-007 | Specific provision | Closing specifically identified allowance | STOCK | C |
| AR-008 | General provision | Closing portfolio/general allowance | STOCK | C |
| AR-009 | Total provision | Non-overlapping specific and general allowances | STOCK | I |
| AR-010 | Net debtors | Gross debtors less allowance | STOCK | I |
| AR-011 | Debtor collection period | Reported days metric; receivable and sales bases require confirmation | REPORTED | C |
| AR-012 | Notes receivable | Separately classified receivables | STOCK | C |
| AR-013 | Expected-credit-loss adjustment | Separately identified allowance/adjustment, with stock versus expense classification | STOCK/FLOW | C |

These debtor categories are visible in M’s machinery/trading schedules; the appendix title also refers to Property. Coverage by division must be verified against the source-system extracts. 

### Ageing design rule

Do not assume “over 30/60/90 days” or “over six/twelve months” are mutually exclusive.

Use exclusive atomic buckets:

```text
receivables: not overdue | 1–30 | 31–60 | 61–90 | 91–365 | >365 days
inventory: approved non-overlapping age bands
```

Then generate cumulative views such as “over 90 days” from atomic buckets. Preserve the report’s original bucket policy until Finance confirms its boundaries.

***

## 14. Capex, people and footprint

### Capital expenditure

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| CPX-001 | Capex actual | Recognized capital additions under the reporting policy | FLOW | C |
| CPX-002 | Cash capex | Cash paid for qualifying capital expenditure; distinct from asset additions | FLOW | C |
| CPX-003 | Property-development expenditure | Expenditure on approved property-development projects | FLOW | C |
| CPX-004 | New/relocated autohaus capex | Project expenditure for new/relocated dealerships | FLOW | I |
| CPX-005 | Replacement capex | Approved replacement expenditure | FLOW | C |
| CPX-006 | Demo/company/courtesy-car additions | Capital additions for these vehicle purposes | FLOW | C |
| CPX-007 | Project budget | Approved project expenditure plan by period/version | FLOW | I |
| CPX-008 | Project forecast | Latest approved expenditure forecast by period/version | FLOW | I |
| CPX-009 | Planned POCC/final inspection | Planned milestone date; acronym and milestone meaning require confirmation | DATE | C |

The capex categories, project/location fields and milestone headings are visible in M, with a summarized capex schedule in O. 

### Workforce and operating footprint

| Code | Metric | Definition / calculation | Type | Status |
|---|---|---|---|---|
| ORG-001 | Headcount | Number of qualifying people at the snapshot date; employee inclusion rules must be explicit | STOCK | C |
| ORG-002 | Budget headcount | Approved planned headcount at a future date/version | STOCK | I |
| ORG-003 | Headcount movement | Closing minus opening count, with transfers identified | MOVEMENT | I |
| ORG-004 | Dealership count | Distinct qualifying dealerships at the snapshot date | STOCK | C |
| ORG-005 | Outlet count | Distinct qualifying outlets at the snapshot date | STOCK | C |
| ORG-006 | Dealership/outlet net change | Closing count minus opening count, retaining the report’s sign convention | MOVEMENT | I |
| ORG-007 | Outsourced staff count | Separate count where available; not silently included in employee headcount | STOCK | C |

M’s headcount schedule is quarterly and notes exclusions for outsourced staff in specified cases. O separately identifies internal employment transfers, demonstrating why divisional headcount movements should not automatically be interpreted as group hiring or reduction. 

***

## 15. Comparison and presentation metrics

Comparisons are calculated views, not duplicate stored actuals.

| Code | Metric | Definition |
|---|---|---|
| CMP-001 | Actual versus forecast — amount | Actual − matched forecast |
| CMP-002 | Actual versus budget — amount | Actual − matched budget |
| CMP-003 | Actual versus prior year — amount | Actual − matched prior-year actual |
| CMP-004 | Estimate versus forecast — amount | Estimate − matched forecast |
| CMP-005 | Estimate versus budget — amount | Estimate − matched budget |
| CMP-006 | Estimate versus prior year — amount | Estimate − matched prior-year actual |
| CMP-007 | Percentage variance | Approved change formula; recommended standard uses comparator magnitude and explicit negative-base policy |
| CMP-008 | Percentage-point variance | Current rate − comparator rate |
| CMP-009 | YTD achievement | YTD actual / approved full-year forecast or budget denominator |
| CMP-010 | Full-year forecast versus budget | FY forecast − FY budget, with optional percentage |
| CMP-011 | Full-year forecast versus prior year | FY forecast − prior-year FY actual |
| CMP-012 | Snapshot movement | Closing balance − comparator-date balance |
| CMP-013 | FX impact | Reported-currency change less constant-currency change under an approved translation method |
| CMP-014 | Variance excluding FX impact | Change computed on a consistent FX basis |
| CMP-015 | Favorable/unfavorable variance | Variance interpreted using metric-specific favorability rules |

These comparison structures are visible throughout the packs; however, the precise negative-base and achievement-denominator policies are not universally stated. 

### Separate value from commentary

Create a structured commentary record containing:

```text
commentary_id
report_release_id
metric_code
context_id
period_id
comparison_version_id
variance_amount
reason_code
narrative
action_owner
action_due_date
approval_status
```

Do not embed material business assumptions only in report text.

***

## 16. Canonical data model

### Recommended model

Use a **fact constellation with shared dimensions**, not one enormous flat table.

Three layers serve different purposes:

1. **Atomic source facts:** transactions, events and snapshots.
2. **Certified reporting facts:** approved period balances, consolidation movements and operational aggregates.
3. **Semantic observations:** governed metric outputs and report-cell mappings.

```mermaid
erDiagram
    DIM_ENTITY ||--o{ FACT_GL_LINE : owns
    DIM_ACCOUNT ||--o{ FACT_GL_LINE : classifies
    DIM_DATE ||--o{ FACT_GL_LINE : dates

    DIM_ENTITY ||--o{ FACT_FINANCE_PERIOD : reports
    DIM_ACCOUNT ||--o{ FACT_FINANCE_PERIOD : classifies
    DIM_PERIOD ||--o{ FACT_FINANCE_PERIOD : covers
    DIM_VERSION ||--o{ FACT_FINANCE_PERIOD : versions

    DIM_ENTITY ||--o{ FACT_ADJUSTMENT_LINE : adjusts
    DIM_ACCOUNT ||--o{ FACT_ADJUSTMENT_LINE : posts
    DIM_SCOPE ||--o{ FACT_ADJUSTMENT_LINE : scopes

    DIM_CONTEXT ||--o{ FACT_METRIC_VALUE : slices
    DIM_METRIC ||--o{ FACT_METRIC_VALUE : defines
    DIM_PERIOD ||--o{ FACT_METRIC_VALUE : covers
    DIM_VERSION ||--o{ FACT_METRIC_VALUE : versions

    DIM_CONTEXT ||--o{ FACT_PLAN_VALUE : slices
    DIM_METRIC ||--o{ FACT_PLAN_VALUE : measures
    DIM_VERSION ||--o{ FACT_PLAN_VALUE : versions

    DIM_CONTEXT ||--o{ FACT_FORECAST_VALUE : slices
    DIM_METRIC ||--o{ FACT_FORECAST_VALUE : predicts
    MODEL_RUN ||--o{ FACT_FORECAST_VALUE : produces

    REPORT_RELEASE ||--o{ FACT_REPORT_CELL : freezes
    FACT_METRIC_VALUE ||--o{ FACT_REPORT_CELL : supports
```

**No double counting:** Journal facts, certified finance-period facts and metric values are different representations of information. They must not be unioned into one additive measure.

***

## 17. Fact-table catalogue

All tables below are proposed implementation structures.

| Table | Grain / business key | Core measures | Main dimensions |
|---|---|---|---|
| `fact_gl_line` | One source journal line | Debit, credit, signed amount, transaction/functional amounts | Entity, account, posting date, document, counterparty, cost centre, source |
| `fact_finance_period` | Entity × account × period × basis × version × applicable analysis slice | Period movement or closing balance, explicitly typed | Entity, account, period, version, basis, currency |
| `fact_adjustment_line` | One adjustment journal line | Signed amount; ownership/elimination contribution | Scope, entity, partner, account, period, adjustment type, approval |
| `fact_vehicle_sale` | One vehicle-sale event/line | Units, recognized revenue, cost, approved GP components | Vehicle, product, brand, condition, channel, sales model, location, date |
| `fact_workshop_event` | One approved throughput event | Throughput count | Workshop, event date, vehicle/brand, event type |
| `fact_workshop_charge` | One workshop charge/revenue line | Parts revenue, labour revenue, costs | Workshop event, revenue stream, product, date |
| `fact_machinery_sale` | One machine/engine sale line | Units, revenue, cost, support contribution | Entity, product, category, location, date |
| `fact_inventory_snapshot` | Item/lot × location × as-of date × version | Quantity, gross value, provision, net value, age | Product, location, owner, age policy |
| `fact_receivable_snapshot` | Invoice/open-item × as-of date × version | Gross exposure, allowance, overdue days | Customer, entity, product/portfolio, due date |
| `fact_portfolio_movement` | Loan/lease contract × movement event | Principal/interest/fee movement | Contract, movement type, currency, entity, date |
| `fact_portfolio_snapshot` | Contract × as-of date × version | Principal, accrued interest, allowance, deferred income | Portfolio, borrower, product, entity |
| `fact_bank_balance` | Bank account × balance type × as-of timestamp | Ledger/available/restricted balance | Bank account, entity, currency |
| `fact_facility_snapshot` | Facility/sublimit × as-of timestamp × version | Limit, utilization, headroom | Bank, facility, entity, currency |
| `fact_property_snapshot` | Property/space × as-of date × version | Leaseable/leased area, cost, carrying value | Property, space type, geography |
| `fact_lease_charge` | Lease contract × charge/accrual period | Contractual charge, recognized rent, cash receipt | Tenant, property, entity, intercompany partner |
| `fact_hotel_daily` | Hotel × business date × approved room segment | Available/sold room-nights, room/F&B/other revenue | Hotel, date, room segment |
| `fact_project_expenditure` | Project × expenditure line/date | Capital addition, cash expenditure, commitments if available | Project, asset category, entity, date |
| `fact_project_milestone` | Project × milestone × version | Planned/forecast/actual dates | Project, milestone, version |
| `fact_headcount_snapshot` | Employee or approved aggregated category × as-of date | Headcount; optional FTE if available | Entity, function, category, location |
| `fact_location_snapshot` | Location × as-of date | Active dealership/outlet flags | Location, brand, entity, geography |
| `fact_investment_movement` | Investee × movement × period × basis/version | Profit share, FX, other movements | Investee, ownership rule, account, scope |
| `fact_plan_value` | Metric × planning context × target period × approved version | Planned numeric value | Metric, context, period, scenario/version |
| `fact_forecast_value` | Metric × context × target period × model run | Forecast, interval bounds/quantiles | Model run, context, metric, period |
| `fact_metric_value` | Metric definition × context × period × basis/version × currency | Certified calculated or source-reported value | Shared semantic dimensions |
| `fact_report_cell` | Report release × page/section × row × column | Published display value and canonical linkage | Report, metric value, source locator |
| `fact_commentary` | Commentary item × report/metric context | Narrative, reason, quantified impact | Metric, period, context, owner |

### Context dimension

`dim_context` is a governed combination of applicable analysis keys. It supports planning and semantic observations without forcing every operational fact to carry every possible dimension.

Suggested fields:

```text
context_id
entity_id
management_unit_id
geography_id
location_id
brand_id
product_group_id
condition_id
sales_model_id
channel_id
revenue_stream_id
cost_category_id
property_id
hotel_id
portfolio_id
project_id
counterparty_id
consolidation_scope_id
operational_perimeter_id
```

Enforce valid combinations. For example, a hotel-only metric should not be stored against an automotive product without a valid business reason.

***

## 18. Core schema specification

### Financial period fact

| Field | Type | Purpose |
|---|---|---|
| `finance_value_id` | UUID / bigint | Surrogate identifier |
| `entity_id` | FK | Legal entity |
| `account_id` | FK | Approved group account |
| `period_id` | FK | Explicit start/end dates |
| `context_id` | FK | Applicable analytical slice |
| `version_id` | FK | Actual/estimate/budget/forecast release |
| `basis_id` | FK | Statutory/management/other basis |
| `value_nature` | Enum | `PERIOD_MOVEMENT`, `CLOSING_BALANCE`, `OPENING_BALANCE` |
| `currency_id` | FK | Currency of stored amount |
| `amount` | Decimal | Amount in base units |
| `source_amount` | Decimal | Original reported amount |
| `source_scale` | Decimal | Original unit multiplier |
| `source_record_id` | FK/text | Source record identity |
| `load_batch_id` | FK | Ingestion lineage |
| `publication_status` | Enum | Provisional, reviewed, certified, restated |
| `valid_from`, `valid_to` | Timestamp | Business-valid version |
| `recorded_at` | Timestamp | Warehouse knowledge time |

**Natural uniqueness:** Entity, account, period, context, version, basis, value nature and currency, plus any required source-partition discriminator.

### Adjustment fact

```text
adjustment_id
adjustment_line_id
adjustment_type
consolidation_scope_id
entity_id
intercompany_partner_id
account_id
period_id
basis_id
version_id
currency_id
signed_amount
reason_code
supporting_document_id
prepared_by
approved_by
approval_timestamp
reversal_period_id
rule_version_id
```

Recommended adjustment types:

```text
INTERCOMPANY_ELIMINATION
UNREALIZED_PROFIT
OWNERSHIP_SHARE
EQUITY_ACCOUNTING
FX_TRANSLATION
MANAGEMENT_ADJUSTMENT
OWNER_BOOK_ADJUSTMENT
RECLASSIFICATION
TOPSIDE_CORRECTION
```

### Metric-value fact

```text
metric_value_id
metric_code
metric_definition_version
context_id
period_id
version_id
basis_id
currency_id
unit_id
value
value_status
quality_status
calculation_run_id
source_observation_id
published_at
```

`value_status` should distinguish:

```text
VALID
NOT_APPLICABLE
NOT_REPORTED
NOT_AVAILABLE
PENDING_CONFIRMATION
SUPPRESSED
INVALID_DENOMINATOR
```

A zero is a value. It must not stand in for unavailable information.

### Plan and scenario version

```text
version_id
scenario_type
version_name
created_at
approved_at
approved_by
forecast_origin_date
actuals_cutoff_date
target_start_date
target_end_date
actuals_release_id
assumption_set_id
parent_version_id
status
locked_at
```

For a 6+6 forecast, the model must identify the actuals cutoff and the six future forecast months. It must not store only one full-year amount if monthly forecasting and cash planning are required.

***

## 19. Metric governance metadata

### Definition table

```text
metric_definition
-----------------
metric_code
definition_version
name
description
definition_status
formula_expression
numerator_metric_code
denominator_metric_code
aggregation_rule
unit_id
currency_behavior
value_nature
favorability_direction
valid_dimension_set
required_dimension_set
effective_from
effective_to
business_owner
approval_status
```

### Source-to-canonical mapping

```text
source_metric_mapping
---------------------
mapping_id
source_document_or_system
source_release
source_page_or_table
source_row_label
source_column_label
source_unit
source_currency
source_scale
source_sign_convention
canonical_metric_code
canonical_context_mapping
canonical_basis_id
canonical_version_id
transformation_rule_id
definition_status
approved_by
```

### Report-cell mapping

```text
report_template_cell
--------------------
template_id
section_id
row_id
column_id
metric_code
context_filter
period_expression
version_expression
basis_expression
currency_expression
display_scale
format_string
subtotal_rule
commentary_rule
```

This turns each report into a **presentation specification**, rather than another place to implement finance calculations.

***

## 20. Source-to-model crosswalk

### Flash report

| Physical pages | Source content | Common-model destination |
|---|---|---|
| F 2–3 | Revenue, operating profit, PAT, divisional estimates, associates/JVs | Finance-period values, investment contributions, scenario/version, report cells |
| F 4–14 | Automotive volumes, revenue, GP tiers, commissions, expenses, ratios | Vehicle/workshop facts, finance values, metric definitions |
| F 15 | Auto-other PAT and unit summaries | Finance/volume measures with subgroup and operational perimeter |
| F 16–17 | Machinery units, revenue, GP and commentary | Machinery facts, finance values, commentary |
| F 18–19 | Property, Trading, Financial Services, Corporate | Finance values, management hierarchy, commentary |

Source: supplied flash report. 

### Monthly management report

| Physical pages | Source content | Common-model destination |
|---|---|---|
| M 2–7 | Group P&L and automotive/divisional summaries | Finance-period facts and semantic metrics |
| M 8–12 | China/Korea automotive detail; SI/VM; workshop | Vehicle, workshop, incentive and GP definitions |
| M 13–17 | Machinery, Property, Trading, Financial Services, finance costs and tax | Shared finance facts plus business dimensions |
| M 18–19 | Associates/JVs and supporting investee results | Investment facts, source-release precedence, ownership rules |
| M 20–30 | Financial position and supporting notes | Balance snapshots, portfolio/investment movements, reserves |
| M 31 | Cash-flow statement and commentary | Cash-flow mapping and bridge components |
| M 32–34 | Capex and projects | Project expenditure and milestone facts |
| M 35–36 | Gearing, liquidity, interest cover and facilities | Treasury snapshots and covenant definitions |
| M 37–40 | Inventory/debtor ageing | Snapshot facts and ageing policies |
| M 41–45 | Rental analysis | Property, space, lease and yield facts |
| M 46–47 | Dealership/outlet and headcount | Location and workforce snapshots |

Source: supplied management report. 

### Operations review

| Physical pages | Source content | Common-model destination |
|---|---|---|
| O 1–4 | Group P&L, adjusted PAT and divisional/associate/JV PAT | Finance metrics, adjustment bridge, scope/version |
| O 5–8 | Automotive and machinery operations | Operational volumes and finance metrics |
| O 9–12 | Property, hotels, yield and FV | Property/hotel facts; owner adjustments |
| O 13–15 | Financial Services, ratios, headcount and footprint | Finance, treasury and organization facts |
| O 16–29 | Divisional appendices and commentary | Shared metrics with finer product/activity dimensions |
| O 30–34 | Balance sheet and notes | Finance balances and asset/portfolio snapshots |
| O 35–37 | Cash flow, capex and tax | Cash-flow, project and tax measures |

Source: supplied operations review. 

***

## 21. Cadence and consolidation design

### Cadence policy

The schedule below is a **proposed operating model**, not evidence that every source currently supports these refresh frequencies.

| Frequency | Extract / refresh | Transform / certify | Consumption |
|---|---|---|---|
| **Daily** | Available sales, service, inventory, AR, bank, loan and hotel events/snapshots | Incremental mapping, duplicate checks, operational reconciliation; indicative consolidation | Cash visibility, operational dashboards, early flash signals |
| **Weekly** | Changed operational data, collections, commitments and treasury inputs | Weekly certified operational snapshot; liquidity forecast refresh | Weekly performance, inventory actions, cash forecast |
| **Monthly** | GL/TB, accruals, tax, ownership results, capex and close adjustments | Closed-period consolidation; FX; intercompany elimination; Finance sign-off | Management report, month-end flash-to-actual bridge |
| **Quarterly** | Quarter-end inputs, reported headcount, updated forecasts and covenant data | Quarter certification; forecast vintage; covenant review | Quarterly review and planning |
| **Half-year** | Half-year close plus approved full-year reforecast | Half-year release and 6+6 forecast | Operations review matching the supplied format |
| **Annual** | Final close, valuations, audit adjustments and approved budget | Annual certification; opening-balance roll; hierarchy/rule versioning | Annual review, annual plan and long-range planning |

### Operational versus accounting certification

```mermaid
flowchart TB
    DAILY["Daily incremental data"]
    OP["Operationally validated data"]
    WEEK["Weekly operational release"]
    MONTH["Month-end close and reconciliations"]
    CERT["Certified monthly finance release"]
    QTR["Quarterly release"]
    HALF["Half-year review"]
    YEAR["Annual audited or final release"]

    DAILY --> OP
    OP --> WEEK
    OP --> MONTH
    MONTH --> CERT
    CERT --> QTR
    CERT --> HALF
    CERT --> YEAR

    CERT --> BRIDGE["Flash-to-actual variance bridge"]
    OP --> BRIDGE
```

### Rules for time aggregation

| Measure | Daily → weekly/monthly | Monthly → quarterly/annual |
|---|---|---|
| Revenue, expenses, GP, cash movements | Sum disjoint recognized movements | Sum disjoint monthly flows |
| Vehicle/service units | Sum events under an approved deduplication rule | Sum compatible period counts |
| Inventory, debt, cash, receivables | Choose required closing snapshot or defined average | Do not sum month-end balances |
| Headcount and locations | Period-end count or separately defined average | Do not sum snapshots |
| Margin, yield, price/unit | Recompute from aggregated components | Recompute |
| Occupancy | Property snapshot basis or area-time basis; hotel room-night basis | Preserve metric-specific weighting |
| FX rate | Apply the relevant rate policy | Do not use an unapproved average of averages |
| YTD published value | Keep as a source observation | Do not add overlapping YTD values |

### Monthly-only information

If only monthly totals exist:

- Store monthly values at monthly grain.
- Do not fabricate daily actuals.
- Weekly views should show available operational measures or clearly labelled estimates.
- If planning requires daily allocation, store it as an allocation/scenario with its own methodology and version.

***

## 22. Extract, transform and consolidate once

### Processing sequence

```mermaid
flowchart LR
    A["1. Land immutable extract"]
    B["2. Validate source contract"]
    C["3. Normalize units and codes"]
    D["4. Map conformed dimensions"]
    E["5. Load atomic facts"]
    F["6. Reconcile to source"]
    G["7. Apply approved adjustments"]
    H["8. Consolidate scope and currency"]
    I["9. Calculate governed metrics"]
    J["10. Certify and publish"]

    A --> B --> C --> D --> E --> F --> G --> H --> I --> J
    F -->|Failed| Q["Quarantine and resolution"]
    Q --> C
```

### Reusable transformation rules

| Rule | Requirement |
|---|---|
| Source identity | Capture source system, business key, extraction time and batch |
| Idempotence | Reprocessing the same extract must not duplicate facts |
| Change handling | Support updates, reversals, deletions and late postings |
| Units | Convert millions/thousands to base units; retain original scale |
| Currency codes | Normalize report aliases, such as RMB/CNY, WON/KRW and NTD/TWD |
| Signs | Preserve original sign and standardize canonical debit/credit or income/expense treatment |
| Account mapping | Effective-dated local account → group account → report line |
| Master data | Effective-dated entity/product/location/counterparty mappings |
| Cutoff | Preserve posting date, event date, settlement date and extraction date |
| Adjustments | Separate source transactions from manual and rule-generated adjustments |
| Quality | Block certification on material unresolved exceptions |
| Restatement | Publish a new version; do not silently overwrite a previously issued report |

### Source precedence

Precedence should be defined for each **metric × entity × period × basis**.

Example policy:

```text
Certified ledger/consolidation release
    supersedes provisional finance submission
        supersedes flash estimate
```

This is a default pattern, not a universal rule. An operational throughput count may remain authoritative in the workshop system, not the GL.

***

## 23. Consolidation and FX

### Consolidation rules

Maintain an effective-dated consolidation rule register:

```text
consolidation_rule_id
scope_id
entity_id
consolidation_method
ownership_percentage
profit_share_percentage
operational_inclusion_flag
effective_from
effective_to
elimination_policy_id
equity_accounting_policy_id
management_adjustment_policy_id
```

Separate these concepts:

- **Full consolidation** of subsidiaries.
- **Equity-accounted contribution** from associates/JVs.
- **Operational statistics** that may include 100% of a JV’s units.
- **Management presentation adjustments**.
- **Bank/covenant exclusion scopes**.

### Currency layers

| Layer | Purpose |
|---|---|
| Transaction currency | Original commercial transaction |
| Functional currency | Entity accounting |
| Presentation currency | Group reporting |
| Constant currency | Performance comparison excluding selected FX effects |
| Scenario currency basis | Forecast/budget assumptions |

Rate metadata should include:

```text
rate_date_or_period
from_currency
to_currency
rate_type
quote_direction
rate_value
rate_source
version_id
approval_status
```

Do not assume one translation method for all accounts. Closing balances, flows, historical equity and translation reserves require approved rules.

### FX comparison

The source reports separately identify FX impact and variance excluding FX in automotive schedules. [ppl-ai-file-upload.s3.amazonaws](https://ppl-ai-file-upload.s3.amazonaws.com/web/direct-files/attachments/11546524/54562b66-7ce3-4de1-aa63-c679078d848f/A_EPM_Group-Current_MR-Reports.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Checksum-Mode=ENABLED&X-Amz-Credential=ASIA2F3EMEYEUAKWHX4M%2F20261104%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20261104T092704Z&X-Amz-Expires=3340&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEOL%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCXVzLWVhc3QtMSJGMEQCIFug2UVgt5lzIBPeb7tmCVzdxibvd1sLyQ2ffCan8CawAiAQCw0n5JbwfqDU5LADkYAZ5asUS%2FFOy3VOvKisqqFGbyqJBQir%2F%2F%2F%2F%2F%2F%2F%2F%2F%2F8BEAEaDDY5OTc1MzMwOTcwNSIMv2BfTimIhzm%2B7%2BTAKt0ENKNZk6RzN3l%2FUELfQ4ZoYV8tqaN9Q5AtjHyc3Q2GVaQ4KhgM%2Btyfw5v77DIa758BJphNd6QFCE0%2B3huw348zKEBYvXseuASKzT8QfbSa5cJyolowy4p%2FIuyctFYNqPbkE7tObi0XPmIihR9VBEOYSwprtvLWAvrOZv5vxrMsfSNqc5MQ3kH0wFaeBjiaG%2FVgVsT2aQnCHc%2F%2Fti77QG6rQx21T8Nrscg2YK9V2EkeYUGHmnnfsTlLK4%2F0RJLp9uYY%2FNXCIWSLSqHvqQlbrQ8frmNITmRrop9XgEbdenZ%2BAqKt%2FEbIVIwygYlFVrUXwdBQzfF586s4xnrJ7wbknu4MLmP7Ry5WK1NAhbUWDwV5Fs5EulfVSXteWlarv2ez7u72RcplJkmDrA%2FrRDkevgI1MU1mvuJBvm6phpKbPA6vyAbRKq4KWbVMxbWRVHOS5AzABI5%2B5T02ne2lob83HXRJNtVlYGbBqzGYSs5HZPBNAmUt%2Fwu3F9zhY64%2F3ymKZQBmBVnePo7qRz9DG578rfoP%2F6ub50l0NaCxf%2F7xxC15HX%2FT6yzXPJko0DM%2FdX5E%2BORo40Ddaa%2FmAoP9BWez5zv5LYa9dy1MQc14jSZX%2B1ounUe5Ve8fEw%2BNtcxczH%2FIuJDxbW%2FW93com6o%2BFRqrp0oqQNKE%2BeELfIgS2Q5iP7xIzjxBm3VpenQ9oG1Fc4IdLipG4c76hd74LEiXGWwxs87blrLI5Bmvr%2BsLrNTo33QVauyhIyd%2BgvsSrL0xpkO234QbND1WgJzPZNmxdOOvo4X8C4f4ZcIYDZ0W3VVyb%2FYwofOr1wY6mQE%2Bnhpl5KlCN62a7k6%2F9eX0ujQU3xSiITQKRCzO42Dj28b3%2B2n5dfRaJ%2FB4ejK9mRwgbaKBpnuXPAT8MBan%2Bq9GBjB%2BDW634Er9ZiXds9tb%2BUOnn31Psl%2B83fYDLdaJ70gK9QVjnHRIhoFqTVsGaPPjjr38yfKn99JKSyc09kuChyxjlJ8VnV09FT5N%2FXw6Xxtkj9hGzxlRuGo%3D&X-Amz-SignedHeaders=host&x-id=GetObject&X-Amz-Signature=2672a081023d5e7d1d9746b0da790c9c741f65bd404f9476209ff80d2c7f6d74)

A proposed semantic bridge is:

```text
reported-currency variance
    = constant-currency variance
    + defined FX translation impact
```

This identity is only valid after agreeing the rate basis, comparator and scope. It must not conflate operating FX gains/losses with presentation-currency translation.

***

## 24. Multi-report publication

### Common semantic datasets

| Dataset | Main content | Reused by |
|---|---|---|
| `finance_performance` | Revenue, GP, operating result, PBT, tax and PAT | All three reports |
| `automotive_performance` | Dealer/agency units, GP tiers, workshop and unit economics | Flash, MR, operations review |
| `machinery_performance` | Machine/engine units, product revenue and GP | Flash, MR, operations review |
| `property_performance` | Rental, area, occupancy, yields and valuation | MR, operations review |
| `hotel_performance` | Room nights, ADR, RevPAR, P&L and owner adjustments | Operations review and ad-hoc |
| `portfolio_performance` | Loan/lease balances, movements and provisions | MR, treasury and planning |
| `financial_position` | Assets, liabilities, equity and balance movements | MR, operations review |
| `cash_liquidity` | Cash, facilities, borrowing, gearing and interest cover | MR, treasury, review |
| `capex_projects` | Additions, expenditure and project milestones | MR, review, planning |
| `organization_capacity` | Headcount, locations and structural changes | MR, review, growth planning |

### Freeze every issued report

A `report_release` should record:

```text
report_release_id
report_type
reporting_period
published_at
finance_data_release_id
operational_data_release_id
plan_version_id
metric_definition_version_set
hierarchy_version_id
fx_version_id
adjustment_release_id
approval_id
```

This allows both:

- **As published:** reproduce the exact issued report.
- **Latest restated:** analyze the most recently approved information.

***

## 25. Scenario-based analysis

Scenario analysis should reuse the same metric definitions while changing a controlled assumption set.

### Scenario structure

```mermaid
flowchart LR
    BASE["Certified actuals and approved baseline"]
    DRV["Versioned assumptions"]
    RULE["Driver relationships"]
    CALC["Deterministic financial calculation"]
    OUT["Scenario P&L, balance sheet and cash flow"]
    COMP["Compare with baseline"]

    BASE --> CALC
    DRV --> RULE --> CALC
    CALC --> OUT --> COMP
    BASE --> COMP
```

### Driver catalogue

These are **proposed** analytical drivers.

| Domain | Example driver | Dependent measures |
|---|---|---|
| Automotive | Units, dealer/agency mix, price, GP per unit, SI/VM assumptions | Revenue, GP, commissions, inventory and cash |
| Workshop | Throughput, parts/labour revenue per throughput, GP margin | Aftersales revenue and GP |
| Machinery | Units by category, price, product-support demand | Revenue, GP and inventory |
| Property | Leaseable area, occupancy, rent per sqm, renewals | Rental revenue, yield and cash receipts |
| Hotel | Available room-nights, occupancy, ADR, F&B assumptions | Room revenue, RevPAR, hotel result |
| Lending | Drawdowns, repayments, rates, fee timing, impairment | Portfolio, income, provisions and cash |
| Treasury | Cash timing, rates, debt mix, FX assumptions | Funding requirement, finance cost, headroom |
| Workforce | Headcount, compensation, hiring timing | Personnel costs |
| Capex | Project timing, additions, payment milestones | Asset balances, depreciation and cash outflow |
| Credit/inventory | Collection timing, ageing, stock policy | Working capital and financing |

### Example scenario questions

- What happens to GP and cash if vehicle sales decline while inventory purchases remain unchanged?
- What is the impact of moving more sales from dealer to agency?
- What funding is needed if receivable collections slow?
- How does a hotel occupancy change affect revenue and owner-adjusted profit?
- What happens to gearing and interest cover if capex is accelerated?
- How does FX affect reported profit versus underlying local-currency performance?

Each scenario should preserve its assumptions, owner, timestamp, baseline version and calculation lineage.

***

## 26. Time-series forecasting

### Required forecasting grain

Use a stable series identity:

```text
metric_code
approved_context
frequency
currency_basis
accounting_basis
consolidation_scope
```

Training observations additionally require a historical release/vintage. The forecast output requires origin date, horizon and model run.

### Forecast data contract

| Field | Purpose |
|---|---|
| `series_id` | Stable metric/context identity |
| `observation_period` | Period being measured |
| `available_at` | When the value became available |
| `value` | Approved observed value |
| `quality_status` | Missing/outlier/corrected flag |
| `actual_release_id` | Historical version used |
| `forecast_origin` | Last point available to the model |
| `target_period` | Future period |
| `model_run_id` | Reproducible forecasting execution |
| `point_forecast` | Central forecast |
| `lower_bound`, `upper_bound` | Interval with defined coverage |
| `quantile` | Optional quantile forecast |
| `driver_version_id` | External/business-driver assumptions |

### Forecasting pipeline

```mermaid
flowchart LR
    HIST["Historical certified observations"]
    VINT["Point-in-time vintage selection"]
    CLEAN["Quality checks and missingness policy"]
    BASE["Naive and seasonal baselines"]
    MODEL["Statistical, ML or driver models"]
    TEST["Rolling-origin backtesting"]
    REC["Hierarchy and accounting reconciliation"]
    APPROVE["Finance review and override"]
    PUB["Versioned forecast publication"]

    HIST --> VINT --> CLEAN
    CLEAN --> BASE
    CLEAN --> MODEL
    BASE --> TEST
    MODEL --> TEST
    TEST --> REC --> APPROVE --> PUB
```

### Forecasting policies

- Forecast at a grain supported by adequate history; do not assume dealership/model-level data is sufficient.
- Compare sophisticated models against simple baselines.
- Separate structural changes from ordinary seasonality.
- Prevent leakage by training only on data available at each historical forecast origin.
- Store Finance overrides separately from the model forecast.
- Reconcile forecasts across business hierarchies.
- Enforce accounting relationships: revenue, GP, cash flow and balance sheet must remain consistent.
- Forecast ratio components and recalculate ratios rather than blindly extrapolating margins.
- Evaluate error and bias by metric, business and horizon.
- Use WAPE/MASE/RMSE or other suitable measures; avoid relying on MAPE for zero or near-zero series.

**Evidence limitation:** The three supplied reporting packs are not, by themselves, a sufficient historical training dataset. Transaction history, prior monthly releases and historical forecast vintages must be obtained before forecasting accuracy can be assessed.

***

## 27. Reconciliation and control framework

### Required controls

| Area | Control |
|---|---|
| Financial completeness | All in-scope entities and expected source partitions received |
| Ledger integrity | Debits/credits or trial-balance controls pass |
| Balance sheet | Assets equal liabilities plus equity within approved tolerance |
| P&L | Revenue/COGS/GP/operating-profit/PAT bridges reconcile |
| Cash flow | Opening cash + movements + FX equals closing cash |
| Inventory | Gross − provision equals net |
| Receivables | Gross − allowance equals net |
| Portfolio | Opening + signed movements equals closing |
| Investment | Opening investment + approved movements equals closing |
| Facilities | Headroom respects utilization and shared-limit rules |
| Property | Leased area does not exceed approved leaseable area without an explained exception |
| Hotel | Sold room-nights do not exceed available room-nights under the approved inventory definition |
| Workforce | Entity/function totals reconcile to approved headcount |
| Consolidation | Intercompany differences, equity accounting and top-side journals approved |
| Report tie-out | Reproduced cells agree with the selected published release |
| Access | Row/column access enforced by entity, scope and sensitive data type |
| Forecasts | Actuals cutoff, assumption set, override and approval recorded |

### Lineage

```mermaid
flowchart LR
    SRC["Source document or system record"]
    MAP["Approved mapping"]
    FACT["Canonical fact"]
    ADJ["Adjustment and consolidation"]
    MET["Metric definition version"]
    CELL["Published report cell"]
    DEC["Scenario or forecast"]

    SRC --> MAP --> FACT --> ADJ --> MET --> CELL
    MET --> DEC
```

For every material report number, a reviewer should be able to trace back to:

> **Published cell → metric version → consolidation/adjustment → canonical facts → source records.**

***

## 28. Definitions requiring priority sign-off

These are the most material unresolved items arising from the supplied report structures.

| Priority | Question | Why it matters |
|---|---|---|
| 1 | What precisely constitutes GP1 and GP3 in each market? | Prevents false comparison and double counting of incentives |
| 2 | How are SI and VM recognized, accrued and allocated? | Affects GP, receivables and forecasts |
| 3 | Which unit counts include JVs, and at what ownership basis? | Aligns operational KPIs with financial scope |
| 4 | What is the approved dealer/agency revenue treatment? | Prevents misleading revenue and margin comparison |
| 5 | What is the workshop throughput counting event? | Controls per-throughput revenue and productivity |
| 6 | What is the workshop absorption-rate numerator and denominator? | Enables comparable regional reporting |
| 7 | What are the exact associate ROS/capping and MBK MA rules? | Controls group PAT, reserves and ratio bridges |
| 8 | Are ageing columns cumulative or exclusive, and based on which dates? | Prevents overstated aged exposure |
| 9 | How are stock-turnover and debtor-collection days calculated? | Controls working-capital comparability |
| 10 | Is rental income contractual run-rate or recognized income in each schedule? | Controls occupancy/yield interpretation |
| 11 | Which property costs and exclusions apply to yield? | Controls property returns |
| 12 | What are the exact hotel owner-book adjustment signs and capital-charge policy? | Separates operating and ownership economics |
| 13 | What defines portfolio provision coverage? | Controls credit-risk reporting |
| 14 | Which cash/deposit/overdraft balances qualify for each cash metric? | Controls liquidity and cash-flow reconciliation |
| 15 | Which bank-specific gearing and interest-cover definitions apply? | Controls covenant reporting |
| 16 | Does “YTD achievement” use FY forecast or FY budget for each column? | Prevents inconsistent performance assessment |
| 17 | Which headcount categories exclude outsourced workers? | Controls cost/productivity comparisons |
| 18 | Are capex schedules recording additions, commitments or cash payments? | Controls balance-sheet and cash planning |
| 19 | What is the correct interest-cover numerator name: EBITA or EBITDA? | Aligns labels with component formula |
| 20 | What is the source precedence when flash and final associate results differ? | Prevents duplicate or stale profit contributions |

The need for these clarifications follows from the report labels, notes and differing presentations; no unsupported policy should be substituted for Finance approval. [ppl-ai-file-upload.s3.amazonaws](https://ppl-ai-file-upload.s3.amazonaws.com/web/direct-files/attachments/11546524/7d76e265-a142-41bd-9121-e5494453c0ec/A_EPM_Flash-Reports.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Checksum-Mode=ENABLED&X-Amz-Credential=ASIA2F3EMEYEUAKWHX4M%2F20261104%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20261104T092704Z&X-Amz-Expires=3340&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEOL%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCXVzLWVhc3QtMSJGMEQCIFug2UVgt5lzIBPeb7tmCVzdxibvd1sLyQ2ffCan8CawAiAQCw0n5JbwfqDU5LADkYAZ5asUS%2FFOy3VOvKisqqFGbyqJBQir%2F%2F%2F%2F%2F%2F%2F%2F%2F%2F8BEAEaDDY5OTc1MzMwOTcwNSIMv2BfTimIhzm%2B7%2BTAKt0ENKNZk6RzN3l%2FUELfQ4ZoYV8tqaN9Q5AtjHyc3Q2GVaQ4KhgM%2Btyfw5v77DIa758BJphNd6QFCE0%2B3huw348zKEBYvXseuASKzT8QfbSa5cJyolowy4p%2FIuyctFYNqPbkE7tObi0XPmIihR9VBEOYSwprtvLWAvrOZv5vxrMsfSNqc5MQ3kH0wFaeBjiaG%2FVgVsT2aQnCHc%2F%2Fti77QG6rQx21T8Nrscg2YK9V2EkeYUGHmnnfsTlLK4%2F0RJLp9uYY%2FNXCIWSLSqHvqQlbrQ8frmNITmRrop9XgEbdenZ%2BAqKt%2FEbIVIwygYlFVrUXwdBQzfF586s4xnrJ7wbknu4MLmP7Ry5WK1NAhbUWDwV5Fs5EulfVSXteWlarv2ez7u72RcplJkmDrA%2FrRDkevgI1MU1mvuJBvm6phpKbPA6vyAbRKq4KWbVMxbWRVHOS5AzABI5%2B5T02ne2lob83HXRJNtVlYGbBqzGYSs5HZPBNAmUt%2Fwu3F9zhY64%2F3ymKZQBmBVnePo7qRz9DG578rfoP%2F6ub50l0NaCxf%2F7xxC15HX%2FT6yzXPJko0DM%2FdX5E%2BORo40Ddaa%2FmAoP9BWez5zv5LYa9dy1MQc14jSZX%2B1ounUe5Ve8fEw%2BNtcxczH%2FIuJDxbW%2FW93com6o%2BFRqrp0oqQNKE%2BeELfIgS2Q5iP7xIzjxBm3VpenQ9oG1Fc4IdLipG4c76hd74LEiXGWwxs87blrLI5Bmvr%2BsLrNTo33QVauyhIyd%2BgvsSrL0xpkO234QbND1WgJzPZNmxdOOvo4X8C4f4ZcIYDZ0W3VVyb%2FYwofOr1wY6mQE%2Bnhpl5KlCN62a7k6%2F9eX0ujQU3xSiITQKRCzO42Dj28b3%2B2n5dfRaJ%2FB4ejK9mRwgbaKBpnuXPAT8MBan%2Bq9GBjB%2BDW634Er9ZiXds9tb%2BUOnn31Psl%2B83fYDLdaJ70gK9QVjnHRIhoFqTVsGaPPjjr38yfKn99JKSyc09kuChyxjlJ8VnV09FT5N%2FXw6Xxtkj9hGzxlRuGo%3D&X-Amz-SignedHeaders=host&x-id=GetObject&X-Amz-Signature=511cdc4fa68c7488f19a071ba6d58af2ad4d4eb38c4eed2d88fe8cc63c958c31)

***

## 29. Implementation sequence

### Phase A — Catalogue and prove

**Proposed duration: 2–3 weeks, subject to access and Finance availability.**

- Confirm source systems and data owners.
- Approve the metric dictionary and high-priority ambiguities.
- Build entity, account, currency and version mappings.
- Capture every supplied report cell’s source locator.
- Select a bounded reporting slice and reproduce it.

### Phase B — Common reporting foundation

**Proposed duration: 4–8 additional weeks for the selected slice.**

- Implement ingestion and reconciliation.
- Build finance-period, adjustment and metric-value facts.
- Implement operational volumes needed for the chosen reports.
- Publish shared P&L and operational semantic datasets.
- Run parallel report tie-outs.

### Phase C — Treasury and extended operations

- Add bank balances, facilities and portfolio movements.
- Add inventory/AR snapshots and ageing.
- Extend property, hotel, capex and organization facts.
- Introduce daily/weekly views only where source detail supports them.

### Phase D — Planning and forecasting

- Add versioned planning drivers and assumptions.
- Implement scenario calculations.
- Assemble sufficient historical series and forecast vintages.
- Backtest, reconcile and approve forecasting models.
- Link forecast outputs to P&L, balance sheet, cash flow and covenants.

```mermaid
flowchart LR
    A["A. Catalogue and reproduce"]
    B["B. Shared reporting foundation"]
    C["C. Treasury and operational extension"]
    D["D. Scenario planning and forecasting"]

    A --> G1{"Definitions approved and pilot tied out"}
    G1 --> B
    B --> G2{"Certified shared data"}
    G2 --> C
    C --> G3{"History and drivers sufficient"}
    G3 --> D
```

### Acceptance criteria

The foundation is ready to scale when:

- Every in-scope report metric has a definition, owner and source mapping.
- Unresolved definitions are explicitly flagged rather than silently calculated.
- Selected report releases can be reproduced within approved tolerances.
- Actuals, estimates, budgets and forecast vintages remain distinct.
- Ratios and time aggregation follow approved rules.
- Operational and financial perimeters are explicit.
- Reprocessing does not duplicate data.
- Previously issued reports remain reproducible after restatements.
- New reports reuse the semantic layer instead of adding another ETL pipeline.
- Scenario and forecasting outputs retain assumptions, lineage and approvals.

***

## Appendix A — Suggested semantic query contract

A report or ad-hoc request should specify:

```yaml
metric:
  code: FIN-003
  definition_version: approved_current

scope:
  consolidation_scope: selected_group_scope
  management_unit: automobile
  geography: all
  operational_perimeter: financial_consolidation

time:
  period_start: requested_start_date
  period_end: requested_end_date
  view: YTD

comparison:
  scenario: forecast
  version: approved_forecast_vintage

basis:
  accounting: management_adjusted
  currency: HKD
  currency_method: approved_reported
  data_release: certified_release

group_by:
  - geography
  - brand
  - sales_model

output:
  amount_scale: million
  include_lineage: true
  include_quality_flags: true
```

The semantic service should reject incompatible combinations or return explicit warnings—for example, requesting a daily breakdown of monthly-only certified values.

***

## Appendix B — Report reconstruction checklist

| Area | Required reconstruction |
|---|---|
| Flash | Group MTD/YTD highlights; divisional operating profit/PAT; automotive templates; machinery and other divisional summaries |
| Management report | P&L; divisions; associates/JVs; balance sheet and notes; cash flow; capex; ratios; facilities; ageing; rental; footprint; headcount |
| Operations review | YTD/FY comparisons; adjusted PAT; operational volumes; property/hotel KPIs; ratios; workforce; appendices; balance sheet; cash flow; capex; tax |

These reconstruction targets correspond to the visible sections of the three supplied PDFs. 

**Release condition:** Mark each section as `REPRODUCED`, `PARTIALLY_REPRODUCED`, `AWAITING_SOURCE_DATA` or `AWAITING_DEFINITION`. A schema design alone must not be represented as a numerically validated reconstruction of the reports.