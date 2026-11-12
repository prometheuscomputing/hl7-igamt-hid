import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material';
import { TreeComponent, TreeNode } from 'angular-tree-component';
import { NodeHelperService } from '../../../shared/services/node-helper.service';
import { nextCopyName } from '../../domain/example-messages.model';
import { CreateDialogComponent } from '../create-dialog/create-dialog.component';

@Component({
  selector: 'app-table-of-content',
  templateUrl: './table-of-content.component.html',
  styleUrls: ['./table-of-content.component.scss'],
})
export class TableOfContentComponent implements OnInit, OnChanges {

  @Input()
  nodes: TreeNode[];
  @Input()
  activeMessageId: string;
  @Input()
  viewOnly = false;
  @Output()
  onCreateExampleMessage: EventEmitter<{ profileId: string, name: string }>;
  @Output()
  onDeleteExampleMessage: EventEmitter<{ messageId: string, name: string }>;
  @Output()
  onDeleteSnippet: EventEmitter<{ messageId: string, snippetId: string, name: string }>;
  @Output()
  onExportExampleMessage: EventEmitter<{ messageId: string, name: string, format: 'html' | 'txt' | 'validation' }>;
  @Output()
  onExportSnippet: EventEmitter<{ messageId: string, snippetId: string, name: string, format: 'html' | 'txt' }>;
  @Output()
  onCopyExampleMessage: EventEmitter<{ messageId: string, name: string }>;
  @Output()
  onCopySnippet: EventEmitter<{ messageId: string, snippetId: string, name: string }>;
  @ViewChild(TreeComponent) private tree: TreeComponent;

  options;

  constructor(private dialog: MatDialog, private nodeHelperService: NodeHelperService) {
    this.options = {
      allowDrag: (node: TreeNode) => {
        return false;
      },
      actionMapping: {
        mouse: {
          click: () => { },
        },
      },
    };
    this.onCreateExampleMessage = new EventEmitter();
    this.onCopyExampleMessage = new EventEmitter();
    this.onCopySnippet = new EventEmitter();
    this.onDeleteExampleMessage = new EventEmitter();
    this.onDeleteSnippet = new EventEmitter();
    this.onExportExampleMessage = new EventEmitter();
    this.onExportSnippet = new EventEmitter();
  }

  deleteExampleMessage(node: TreeNode) {
    const message = node.data;
    this.onDeleteExampleMessage.emit({
      messageId: message.id,
      name: message.label || message.name,
    });
  }

  deleteSnippet(node: TreeNode) {
    const snippet = node.data;
    this.onDeleteSnippet.emit({
      messageId: snippet.messageId,
      snippetId: snippet.id,
      name: snippet.label || snippet.name,
    });
  }

  exportExampleMessage(node: TreeNode, format: 'html' | 'txt' | 'validation') {
    const message = node.data;
    this.onExportExampleMessage.emit({
      messageId: message.id,
      name: message.label || message.name,
      format,
    });
  }

  exportSnippet(node: TreeNode, format: 'html' | 'txt') {
    const snippet = node.data;
    this.onExportSnippet.emit({
      messageId: snippet.messageId,
      snippetId: snippet.id,
      name: snippet.label || snippet.name,
      format,
    });
  }

  expandAll() {
    this.tree.treeModel.expandAll();
  }

  collapseAll() {
    this.tree.treeModel.collapseAll();
  }

  filter(value: string) {
    this.tree.treeModel.filterNodes((node) => {
      return this.nodeHelperService
        .getFilteringLabel(node.data.fixedName, node.data.variableName).toLowerCase()
        .startsWith(value.toLowerCase());
    });
  }

  createExampleMessage(id: string) {
    const existingNames = this.messageNamesForProfile(id);
    this.dialog.open(CreateDialogComponent, {
      data: {
        title: 'Create Example Message',
        kind: 'example message',
        existingNames,
      }
    }).afterClosed().subscribe((data) => {
      if (data && data.name) {
        this.onCreateExampleMessage.emit({
          name: data.name,
          profileId: id,
        });
      }
    });
  }

  copyExampleMessage(node: TreeNode) {
    const message = node.data;
    const existingNames = this.messageNamesForProfile(message.profileId);
    this.dialog.open(CreateDialogComponent, {
      data: {
        title: 'Copy Example Message',
        kind: 'example message',
        name: nextCopyName(message.label || message.name, existingNames),
        existingNames,
        confirmLabel: 'Copy',
      }
    }).afterClosed().subscribe((data) => {
      if (data && data.name) {
        this.onCopyExampleMessage.emit({
          messageId: message.id,
          name: data.name,
        });
      }
    });
  }

  copySnippet(node: TreeNode) {
    const snippet = node.data;
    const existingNames = this.snippetNamesForMessage(node);
    this.dialog.open(CreateDialogComponent, {
      data: {
        title: 'Copy Snippet',
        kind: 'snippet',
        name: nextCopyName(snippet.label || snippet.name, existingNames),
        existingNames,
        confirmLabel: 'Copy',
      }
    }).afterClosed().subscribe((data) => {
      if (data && data.name) {
        this.onCopySnippet.emit({
          messageId: snippet.messageId,
          snippetId: snippet.id,
          name: data.name,
        });
      }
    });
  }

  private messageNamesForProfile(profileId: string): string[] {
    const profile = (this.nodes || []).find((node) => node.id === profileId || (node.data && node.data.id === profileId));
    const messages = (profile && (profile.children || (profile.data && profile.data.children))) || [];
    return messages.map((message) => message.label || message.name || (message.data && (message.data.label || message.data.name))).filter((name) => !!name);
  }

  private snippetNamesForMessage(snippetNode: TreeNode): string[] {
    const parent = snippetNode.parent;
    const siblings = (parent && parent.data && parent.data.children) || [];
    return siblings.map((snippet) => snippet.label || snippet.name).filter((name) => !!name);
  }

  ngOnInit() {
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.nodes && this.nodes && this.structureChanged(changes.nodes.previousValue, changes.nodes.currentValue)) {
      setTimeout(() => {
        if (this.tree && this.tree.treeModel) {
          this.tree.treeModel.expandAll();
        }
      }, 100);
    }
  }

  private structureChanged(previous: any[], current: any[]): boolean {
    return this.nodeKeys(previous) !== this.nodeKeys(current);
  }

  private nodeKeys(nodes: any[]): string {
    if (!nodes || !nodes.length) {
      return '';
    }
    return nodes.map((node) => {
      const children = node.children || (node.data && node.data.children) || [];
      return (node.id || (node.data && node.data.id) || '') + '[' + this.nodeKeys(children) + ']';
    }).join(',');
  }

}
