import { Component, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import * as fromPageMessages from '../../../dam-framework/store/messages/index';

@Component({
  selector: 'app-about',
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss'],
})
export class AboutComponent implements OnInit {

  constructor(private store: Store<any>) {
  }

  ngOnInit() {
    this.store.dispatch(new fromPageMessages.ClearAll());
  }

}
