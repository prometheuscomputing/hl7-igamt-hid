import { Injectable } from '@angular/core';
import { Type } from '../../shared/constants/type.enum';
import { IContent } from '../../shared/models/content.interface';
import { INarrativeCard, INarrativePublishModel } from '../components/narrative-publish/narrative-card.model';

/**
 * Maps IGAMT Ig.content TextSection trees into narrative-card publish models.
 * Only Type.TEXT nodes become cards (technical registries stay out of the card TOC).
 */
@Injectable({ providedIn: 'root' })
export class NarrativeCardMapperService {

  build(content: IContent[], guideTitle?: string): INarrativePublishModel {
    const cards: INarrativeCard[] = [];
    const order: string[] = [];
    const roots = (content || []).slice().sort((a, b) => a.position - b.position);

    for (const section of roots) {
      this.walk(section, null, '' + section.position, cards, order);
    }

    return {
      title: guideTitle || 'Implementation Guide',
      cards,
      order,
    };
  }

  private walk(
    section: IContent,
    parentId: string | null,
    path: string,
    cards: INarrativeCard[],
    order: string[],
  ): void {
    if (!section) {
      return;
    }

    if (section.type === Type.TEXT) {
      const children = (section.children || []).slice().sort((a, b) => a.position - b.position);
      const textChildren = children.filter((c) => c.type === Type.TEXT);
      const card: INarrativeCard = {
        id: section.id,
        number: path,
        title: section.label || 'Untitled section',
        slug: this.slugify(path, section.label),
        parentId,
        narrativeHtml: typeof section.description === 'string' ? section.description : (section.description || ''),
        requirements: section.requirements || [],
        childIds: textChildren.map((c) => c.id),
      };
      cards.push(card);
      order.push(section.id);

      for (const child of children) {
        if (child.type === Type.TEXT) {
          this.walk(child, section.id, path + '.' + child.position, cards, order);
        }
      }
      return;
    }

    // Non-narrative nodes (e.g. PROFILE): descend only to find nested TEXT if any
    const children = (section.children || []).slice().sort((a, b) => a.position - b.position);
    for (const child of children) {
      this.walk(child, parentId, path + '.' + child.position, cards, order);
    }
  }

  private slugify(path: string, label: string): string {
    const base = (path + '-' + (label || ''))
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    return base || path.replace(/\./g, '-');
  }

  byId(model: INarrativePublishModel): { [id: string]: INarrativeCard } {
    const map: { [id: string]: INarrativeCard } = {};
    (model.cards || []).forEach((c) => map[c.id] = c);
    return map;
  }
}
