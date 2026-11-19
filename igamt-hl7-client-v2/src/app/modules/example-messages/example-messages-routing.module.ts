import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DamWidgetContainerComponent } from '../dam-framework/components/data-widget/dam-widget-container/dam-widget-container.component';
import { DataLoaderGuard } from '../dam-framework/guards/data-loader.guard';
import { WidgetDeactivateGuard } from '../dam-framework/guards/widget-deactivate.guard';
import { WidgetSetupGuard } from '../dam-framework/guards/widget-setup.guard';
import { EXAMPLE_MESSAGES_WIDGET_ID, ExampleMessagesContainerComponent } from './components/example-messages-container/example-messages-container.component';
import { ExampleMessagesActionTypes, LoadExampleMessages, OpenExampleActorsEditor, OpenExampleMessageEditor, OpenExampleScenarioEditor, OpenExampleScenarioListEditor, OpenExampleSnippetEditor } from 'src/app/root-store/example-messages/example-messages.actions';
import { ScenarioBoardComponent } from './components/scenario-board/scenario-board.component';
import { ActorsEditorComponent } from './components/actors-editor/actors-editor.component';
import { ScenarioListComponent } from './components/scenario-list/scenario-list.component';
import { MessageEditorComponent } from './components/message-editor/message-editor.component';
import { SnippetEditorComponent } from './components/snippet-editor/snippet-editor.component';
import { EditorActivateGuard, EditorDeactivateGuard } from '../dam-framework';
import { EditorID } from '../shared/models/editor.enum';


const routes: Routes = [
  {
    data: {
      widgetId: EXAMPLE_MESSAGES_WIDGET_ID,
      routeParam: 'igId',
      loadAction: LoadExampleMessages,
      successAction: ExampleMessagesActionTypes.LoadExampleMessagesSuccess,
      failureAction: ExampleMessagesActionTypes.LoadExampleMessagesFailure,
      redirectTo: ['error'],
      component: ExampleMessagesContainerComponent,
    },
    component: DamWidgetContainerComponent,
    canActivate: [
      WidgetSetupGuard,
      DataLoaderGuard,
    ],
    canDeactivate: [
      WidgetDeactivateGuard,
    ],
    path: ':igId',
    children: [
      {
        path: 'message/:messageId',
        component: MessageEditorComponent,
        canActivate: [EditorActivateGuard],
        canDeactivate: [EditorDeactivateGuard],
        data: {
          editorMetadata: {
            id: EditorID.EXAMPLE_MESSAGE,
            title: 'Message',
          },
          onLeave: {
            saveEditor: true,
            saveTableOfContent: true,
          },
          action: OpenExampleMessageEditor,
          idKey: 'messageId',
        },
      },
      {
        path: 'actors',
        component: ActorsEditorComponent,
        canActivate: [EditorActivateGuard],
        data: {
          editorMetadata: {
            id: EditorID.EXAMPLE_ACTORS,
            title: 'Actors',
          },
          action: OpenExampleActorsEditor,
          idKey: 'igId',
        },
      },
      {
        path: 'scenarios',
        component: ScenarioListComponent,
        canActivate: [EditorActivateGuard],
        data: {
          editorMetadata: {
            id: EditorID.EXAMPLE_SCENARIO_LIST,
            title: 'Scenarios',
          },
          action: OpenExampleScenarioListEditor,
          idKey: 'igId',
        },
      },
      {
        path: 'scenario/:scenarioId',
        component: ScenarioBoardComponent,
        canActivate: [EditorActivateGuard],
        data: {
          editorMetadata: {
            id: EditorID.EXAMPLE_SCENARIO,
            title: 'Scenario',
          },
          action: OpenExampleScenarioEditor,
          idKey: 'scenarioId',
        },
      },
      {
        path: 'message/:messageId/snippet/:snippetId',
        component: SnippetEditorComponent,
        canActivate: [EditorActivateGuard],
        data: {
          editorMetadata: {
            id: EditorID.EXAMPLE_SNIPPET,
            title: 'Snippet',
          },
          action: OpenExampleSnippetEditor,
          idKey: 'snippetId',
        },
      },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ExampleMessagesRoutingModule { }
