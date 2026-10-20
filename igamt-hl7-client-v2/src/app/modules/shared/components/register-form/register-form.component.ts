import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidatorFn, Validators} from '@angular/forms';
import {IRegistration} from 'src/app/modules/dam-framework/models/authentication/registration.class';
import {passwordValidator} from '../../validators/password-validator';

@Component({
  selector: 'app-register-form',
  templateUrl: './register-form.component.html',
  styleUrls: ['./register-form.component.css'],
})
export class RegisterFormComponent implements OnInit {
  notice = 'Application for the use of the Implementation Guide Authoring and Management Tool (IGAMT) does ' +
    'not guarantee that the applicant will be granted approval for use. The tool is part of the ' +
    'Standards and Interoperability Testing Tools (SITT), hosted and maintained by Prometheus ' +
    'Computing, LLC, and your registration information will not be visible to users other than the ' +
    'tool administrators and yourselves. Information provided in the tool does not imply endorsement ' +
    'of any particular product, service, organization, company, information provider, or content by ' +
    'NIST, CDC, ONC, AIRA, or Prometheus Computing, LLC. This software was originally developed by ' +
    'employees and contractors of the National Institute of Standards and Technology (NIST). ' +
    'Pursuant to Title 17, United States Code, Section 105, works created by NIST employees within ' +
    'the scope of their employment are not subject to copyright protection in the United States and ' +
    'reside in the public domain. Neither NIST nor Prometheus Computing, LLC assumes responsibility ' +
    'for its use by other parties, and no guarantees, expressed or implied, are made about its ' +
    'quality, reliability, or any other characteristic. The outcome of the use of the tool can be ' +
    'redistributed and/or modified freely provided that any derivative works bear some notice that ' +
    'they are derived from it, and any modified versions bear some notice that they have been ' +
    'modified.';

  registrationForm: FormGroup;
  @Output() submitEvent = new EventEmitter<IRegistration>();

  constructor() {
    this.registrationForm = new FormGroup({
      fullName: new FormControl(
        '',
        [
          Validators.required,
        ],
      ),
      email: new FormControl(
        '',
        [
          Validators.email,
          Validators.required,
        ],
      ),
      username: new FormControl(
        '',
        [
          Validators.required,
          Validators.minLength(6),
          noSpacesValidator(),
        ],
      ),
      password: new FormControl(
        '',
        [
          Validators.required,
          Validators.minLength(6),
        ],
      ),
      confirm: new FormControl(
        '',
      ),
      signedConfidentialityAgreement: new FormControl(
        false,
        [
          this.signedConfidentialityAgreementValidator(),
        ],
      ),
    });
    this.registrationForm.get('confirm').setValidators([Validators.required, passwordValidator(this.registrationForm)]);
  }

  refreshConfirmPasswordValidation() {
    this.registrationForm.get('confirm').updateValueAndValidity();
  }

  submit() {
    this.submitEvent.emit(this.registrationForm.getRawValue());
  }

  signedConfidentialityAgreementValidator(): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } => {
      return !control.value ? {unsigned: {value: control.value}} : null;
    };
  }

  ngOnInit() {
  }

}
export function noSpacesValidator(): ValidatorFn {
  return (control: FormControl) => {
    const value = control.value;
    const hasSpaces = /\s/.test(value); // Check for spaces using regex
    return hasSpaces ? { hasSpaces: 'No space allowed' } : null; // Validation error if spaces are found
  };
}
