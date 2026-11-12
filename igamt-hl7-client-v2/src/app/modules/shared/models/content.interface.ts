import {Type} from '../constants/type.enum';
import {DeltaAction} from './delta';

export type ConformanceVerb = 'SHALL' | 'SHOULD' | 'MAY';

export interface INarrativeRequirement {
  id: string;
  position: number;
  verb: ConformanceVerb;
  text: string;
}

export interface IContent {
  id: string;
  description?: any;
  type: Type;
  position: number;
  label: string;
  delta?: DeltaAction;
  children: IContent[];
  // For MESSAGESECTION type: references to example messages
  messageId?: string;
  snippetId?: string;
  /** Optional structured narrative requirements (SHALL/SHOULD/MAY). */
  requirements?: INarrativeRequirement[];
}
