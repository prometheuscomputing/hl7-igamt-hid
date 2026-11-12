export interface INarrativeCardLink {
  label: string;
  href?: string;
  external?: boolean;
}

export interface INarrativeCard {
  id: string;
  number: string;
  title: string;
  slug: string;
  parentId: string | null;
  /** Froala HTML from IGAMT TextSection.description */
  narrativeHtml: string;
  /** Structured SHALL/SHOULD/MAY requirements when present */
  requirements?: import('../../../shared/models/content.interface').INarrativeRequirement[];
  childIds: string[];
}

export interface INarrativePublishModel {
  title: string;
  cards: INarrativeCard[];
  /** depth-first order of card ids for Prev/Next */
  order: string[];
}
