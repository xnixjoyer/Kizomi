# Shared Kizomi UI parity contract

## Core rule

Kizomi has one visual product. The active provider determines data access and supported actions, not a separate navigation shell or alternate design language.

## Target architecture

### Shared presentation layer

The following surfaces must be provider-neutral:

- app scaffold, bottom navigation and adaptive navigation rail;
- Discover and search;
- Library;
- media details;
- list-entry editor;
- Account/Profile;
- Settings;
- calendar and widgets;
- loading, empty, unavailable and error states.

Shared composables consume neutral UI models and capability state. They must not import MAL transport DTOs or AniList GraphQL response models.

### Provider adapters

Provider-specific implementations are responsible for:

- endpoint or GraphQL operation selection;
- authentication and token lifecycle;
- paging and request limits;
- mapping provider fields into neutral domain models;
- converting typed user edits into exactly one provider request;
- capability declaration;
- provider-specific error translation.

### Typed identity

Every media reference must include:

- active provider;
- media type;
- provider-native media ID.

IDs from different providers must never be treated as interchangeable integers. A shared screen may display a neutral model, but every read or write returns through the same typed provider identity.

## Navigation contract

- Use the existing typed Compose Navigation host.
- Add one provider-neutral media-details route rather than local screen-selection state.
- Route arguments must survive process recreation.
- Invalid arguments render a recoverable error screen.
- Back navigation restores source destination, filters and scroll position.
- Related/recommended entries navigate through the same route contract.
- Deep links may only be enabled when their provider identity is unambiguous.

## Discover contract

- Reuse existing Kizomi card components, section headers, placeholders and adaptive layouts.
- Sections are driven by capabilities and available provider queries.
- Search uses a shared query/filter model with provider-specific validation.
- Paging errors do not destroy already loaded content.
- Refresh is explicit and does not create uncontrolled request bursts.

## Library contract

- Reuse the original list/grid modes, status filters, sort controls, search and edit interactions.
- The active provider supplies list statuses, score format, progress semantics and editable fields.
- Unsupported controls are omitted or disabled with a neutral explanation.
- Writes are single-target, cancellable where possible and recoverable on failure.
- A successful write is verified against provider data before being treated as durable.

## Details contract

The shared details screen should retain Kizomi's existing hierarchy and progressively populate:

- hero/cover imagery;
- title and alternative titles;
- list status and quick actions;
- synopsis and metadata;
- airing/publication information;
- relations and recommendations;
- characters and staff;
- statistics and score distribution;
- reviews, trailers and external links where officially available.

No blank section should appear merely to imitate another provider. Capability absence is a valid state.

## Account and Settings contract

- Shared appearance, language, accessibility, storage, update and navigation settings remain identical.
- Provider-specific account details live inside a shared account/settings hierarchy.
- The provider-change and local-data deletion controls retain the existing destructive safety contract.
- Debug diagnostics are compiled only for debug-capable variants.

## Design and accessibility acceptance

Equivalent capabilities must use the same:

- Material theme and color tokens;
- typography scale;
- component shapes;
- spacing system;
- icon language;
- animation and transition patterns;
- content descriptions and touch-target rules;
- phone/tablet/foldable adaptations.

Provider names may appear where context is necessary, but the app should read and feel like Kizomi rather than separate embedded clients.

## Migration strategy

1. Fix details routing and session restoration in the existing path.
2. Introduce neutral models and provider interfaces.
3. Move MAL into shared Discover and Details.
4. Move MAL into shared Library and editor.
5. Move Account/Settings, calendar and widgets.
6. Delete the obsolete MAL shell only when no production route depends on it.

At every step, AniList behavior must remain covered by regression tests.

## Non-goals

- copying the UI or source of another application;
- inventing unsupported MAL endpoints;
- silently using the inactive provider to fill missing sections;
- transferring user list data between providers;
- forcing feature-shaped placeholders for unavailable capabilities.