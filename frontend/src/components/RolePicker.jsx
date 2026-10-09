const descriptions = {
  Customer: 'Order and track meals',
  Restaurant: 'Accept and prepare orders',
  Courier: 'Pick up and deliver orders',
  Operator: 'Oversee the whole platform',
};

export default function RolePicker({ roles, value, onChange }) {
  return (
    <fieldset className="role-picker">
      <legend>Joining as</legend>
      <div className="role-grid">
        {roles.map(role => (
          <label key={role} className="role-option">
            <input type="radio" name="role" value={role} checked={value === role} onChange={() => onChange(role)} />
            <span className="role-name">{role}</span>
            <span className="role-desc">{descriptions[role]}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
